import paho.mqtt.client as mqtt
from datetime import datetime
import ssl
import threading
import time
import json
import csv
import os 
import requests
import random  
import smbus
from time import sleep
from mpu6050 import mpu6050
import math
import RPi.GPIO as GPIO
import time
import board
import busio
import adafruit_si7021
import serial
import adafruit_gps
import asyncio
import subprocess
import tfmBluetooth

from telegram import Bot
from telegram.error import BadRequest
from RPLCD.gpio import CharLCD

from cryptography.hazmat.primitives.asymmetric import dh
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.backends import default_backend
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.primitives import padding


mqttClient = None
sensorMPU6050= mpu6050(0x68)
lcd = CharLCD(
    numbering_mode=GPIO.BCM,
    cols=16, rows=2,  # Change these values if you have a different size LCD
    pin_rs=7, pin_e=8,
    pins_data=[13, 6, 5, 11])

# thresholds for accident detection
ACCEL_CRASH_THRESHOLD = 5 
GYRO_ROLLOVER_THRESHOLD = 200
ROLLOVER_Z_THRESHOLD = 2  
GYRO_SHARPTURN_THRESHOLD = 150

# gpio pins for the actuators
BUZZER_PIN = 25
VENTILATION_PIN = 24
LIGHTS_PIN = 12

# telegram bot token
TELEGRAM_TOKEN = '6235291411:AAGJ45cR4-rgytzCL78JLeYlxpO3vzElPn8'

# topics used
topicPubData ='tfm/data'
topicPubCommands = 'tfm/commandsPython'
topicSubCommands = 'tfm/commandsAndroid'
topicSubContacts = 'tfm/contactAndroid'
topicSubSetUp = 'tfm/setUpAndroid'

# shared key for encription/decription
shared_key_retreived = None

# system variables set by the user
temperatureThreshold = None
automaticVentilation = None
automaticVentilationStatus = False
startBuzzer = None
accidentDetection = None
startVentilation = None

# configuration information set by the user
userName = None
userPhone = None
carLicense = None
carBrand = None
carModel = None
carColor = None

# store the last coordinates obtained
latitudeValue = None
longitudeValue = None





def on_connect(client, userdata, flags, rc):
    print(f'Connected with result code {rc}')

    client.subscribe(topicSubCommands)
    print(f"Subscrived to: '{topicSubCommands}'")
    client.subscribe(topicSubContacts)
    print(f"Subscrived to: '{topicSubContacts}'")
    client.subscribe(topicSubSetUp)
    print(f"Subscrived to: '{topicSubSetUp}'")
    

def on_message(client, userdata, msg):
    topic = msg.topic
    print(topic)

    if(topic == topicSubCommands):
        handleCommandMsg(msg.payload)

    elif(topic == topicSubContacts):
        print("Received contact")
        handleContactMsg(msg.payload)
    
    elif(topic == topicSubSetUp):
        print("Received set Up configuration")
        handleSetUpMsg(msg.payload)

    else:
        print("Received a message from an unknown topic")





def handleCommandMsg(mensaje):
    global temperatureThreshold
    global automaticVentilation
    global automaticVentilationStatus
    global startBuzzer
    global accidentDetection
    global startVentilation

    decrypted_data = decrypt_data(mensaje, shared_key_retreived)
    decrypted_string = decrypted_data.decode()
    print(f"Command msg received: '{decrypted_string}'")

    if decrypted_string == "cmd-findCar":
        # command to do a pattern with the buzzer and the car lights
        for i in range(1, 4):
            GPIO.output(BUZZER_PIN, GPIO.HIGH)
            GPIO.output(LIGHTS_PIN, GPIO.HIGH)  
            time.sleep(0.6)  
            GPIO.output(BUZZER_PIN, GPIO.LOW)  
            GPIO.output(LIGHTS_PIN, GPIO.LOW) 
            time.sleep(0.4) 
    else:
        #command with the system variables new values
        try:
            # the command comes as a JSON
            data = json.loads(decrypted_string)
            
            # extract values
            set_variables = data['setVariables']
            temperatureThreshold = set_variables['temperatureThreshold']
            automaticVentilation = set_variables['automaticVentilation']
            startVentilation = set_variables['startVentilation']
            startBuzzer = set_variables['startBuzzer']
            accidentDetection = set_variables['accidentDetection']
            
          
            print("\n")
            print(f"Temperature Threshold: {temperatureThreshold}")
            print(f"Automatic Ventilation: {automaticVentilation}")
            print(f"Start Ventilation: {startVentilation}")
            print(f"Start Buzzer: {startBuzzer}")
            print(f"Accident Detection: {accidentDetection}")
            print("\n")

            if startBuzzer:
                GPIO.output(BUZZER_PIN, GPIO.HIGH)
            else:
                GPIO.output(BUZZER_PIN, GPIO.LOW)

            if not automaticVentilation:
                automaticVentilationStatus = False

            if startVentilation:
                # sent tho thingsboard when the ventilation is on
                GPIO.output(VENTILATION_PIN, GPIO.HIGH)
                data = {
                    "isFanActive": True
                }
                urlTelemetry = "https://demo.thingsboard.io/api/v1/rdtiahf5espbzqxczttn/telemetry"
                sendThingsboardData(data, urlTelemetry)
            else:
                if not automaticVentilation:
                    # sent tho thingsboard when the ventilation is off
                    GPIO.output(VENTILATION_PIN, GPIO.LOW)
                    data = {
                        "isFanActive": False
                    }
                    urlTelemetry = "https://demo.thingsboard.io/api/v1/rdtiahf5espbzqxczttn/telemetry"
                    sendThingsboardData(data, urlTelemetry)
                else:
                    if not automaticVentilationStatus:
                        # sent tho thingsboard when the ventilation is off
                        GPIO.output(VENTILATION_PIN, GPIO.LOW)
                        data = {
                            "isFanActive": False
                        }
                        urlTelemetry = "https://demo.thingsboard.io/api/v1/rdtiahf5espbzqxczttn/telemetry"
                        sendThingsboardData(data, urlTelemetry)
        except json.JSONDecodeError:
            print("Failed to parse JSON data")


    
    


def handleContactMsg(mensaje):
    # recieve the updated contacts list
    decrypted_data = decrypt_data(mensaje, shared_key_retreived)
    decrypted_text = decrypted_data.decode()
    print(f"Recived contact list: '{decrypted_text}'")
    store_contacts(decrypted_text)
    print(f"Retreived the contacts ids: {retrieve_contact_ids()}")


def handleSetUpMsg(mensaje):
    # recieve the user and car information

    global userName
    global userPhone
    global carLicense
    global carBrand
    global carModel
    global carColor


    decrypted_data = decrypt_data(mensaje, shared_key_retreived)
    decrypted_text = decrypted_data.decode()

    data = json.loads(decrypted_text)
    

    print("\n")
    userName = data['userName']
    userPhone = data['userPhone']
    carLicense = data['carLicense']
    carBrand = data['carBrand']
    carModel = data['carModel']
    carColor = data['carColor']
    print("\n")
    
    
    print(f"User Name: {userName}")
    print(f"User Phone: {userPhone}")
    print(f"Car License: {carLicense}")
    print(f"Car Brand: {carBrand}")
    print(f"Car Model: {carModel}")
    print(f"Car Color: {carColor}")


    

def connectMqtt():

    mqttClient.on_connect = on_connect
    mqttClient.on_message = on_message

    mqttClient.tls_set(
        ca_certs=None,certfile=None,keyfile=None,cert_reqs=ssl.CERT_REQUIRED,tls_version=ssl.PROTOCOL_TLS,ciphers=None
    )

    
    mqttClient.tls_insecure_set(False)

    # set the credentials
    mqttClient.username_pw_set("tfmBroker", "102001mLc")

    mqttClient.connect("fe16d2d4407449d097cd113952053567.s1.eu.hivemq.cloud", 8883)

    # start the MQTT client
    mqttClient.loop_start()


def collect_location_data():

    global latitudeValue
    global longitudeValue

    uart = serial.Serial("/dev/ttyUSB0", baudrate=9600, timeout=10)

    # create GPS instance and set up
    gps = adafruit_gps.GPS(uart, debug=False)  # Use UART/pyserial
    gps.send_command(b"PMTK314,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0")
    gps.send_command(b"PMTK220,1000")

    last_print = time.monotonic()

    while True:

        # to simulate the GPS value retreived if there is no good connection
        '''
        latitude=40.407213
        longitude=-3.608760
        data = {
            'latitude': latitude,
            'longitude': longitude,
        }
        print(f"Collected location: {data}")
        print("")
        latitudeValue = latitude
        longitudeValue = longitude

        data_bytes = json.dumps(data).encode('utf-8')

        # send data to the mobile using mqtt
        encrypted_data = encrypt_data(data_bytes, shared_key_retreived)
        mqttClient.publish(topicPubData, encrypted_data)

        # publish data in thingsboard (using https requests)
        sendThingsboardData(data, "https://demo.thingsboard.io/api/v1/rdtiahf5espbzqxczttn/telemetry")
        sleep(2)
        '''

        
        gps.update()
        # Every second print out current location details if there's a fix.
        current = time.monotonic()
        if current - last_print >= 1.0:
            last_print = current
            if not gps.has_fix:
                # Try again if we don't have a fix yet.
                print("Waiting for fix...")
                continue
            # We have a fix! (gps.has_fix is true)
            # Print out details about the fix like location, date, etc.
            print("\nLocation:" )  # Print a separator line.
            print(
                "Fix timestamp: {}/{}/{} {:02}:{:02}:{:02}".format(
                    gps.timestamp_utc.tm_mon,  # Grab parts of the time from the
                    gps.timestamp_utc.tm_mday,  # struct_time object that holds
                    gps.timestamp_utc.tm_year,  # the fix time.  Note you might
                    gps.timestamp_utc.tm_hour,  # not get all data like year, day,
                    gps.timestamp_utc.tm_min,  # month!
                    gps.timestamp_utc.tm_sec,
                )
            )
            print("Latitude: {0:.6f} degrees".format(gps.latitude))
            print("Longitude: {0:.6f} degrees".format(gps.longitude))
            latitude = gps.latitude
            longitude = gps.longitude
            latitudeValue = latitude
            longitudeValue = longitude
            data = {
            'latitude': latitude,
            'longitude': longitude,
            }
            

            data_bytes = json.dumps(data).encode('utf-8')

            # Encrypt the data
            encrypted_data = encrypt_data(data_bytes, shared_key_retreived)

            # Publish encrypted data
            mqttClient.publish(topicPubData, encrypted_data)

            #publish data in thingsboard (using https requests)
            sendThingsboardData(data, "https://demo.thingsboard.io/api/v1/rdtiahf5espbzqxczttn/telemetry")
            sleep(2)
        




def collect_accelerometer_data():

    global accidentDetection
    global sensorMPU6050
    
    while True:
        try:
            # get values
            data_accel = sensorMPU6050.get_accel_data()
            data_gyro = sensorMPU6050.get_gyro_data()

            # round values
            data_accel = {k: round(v, 2) for k, v in data_accel.items()}
            data_gyro = {k: round(v, 2) for k, v in data_gyro.items()}

            detected = False

            if accidentDetection:

                # check for crash and rollover
                if detect_rollover(data_accel, data_gyro):
                    print("")
                    print("Rollover detected!")
                    detected = True
                
                else:
                    #since the crash uses the accelerometer when it roll over the gravity actuates over X and y, so can be detected
                    if detect_crash(data_accel):
                        print("")
                        print("Crash detected!")
                        detected = True
                        
                
            

                if detected:
                    print(f"Accelerometer data: x = {data_accel['x']}g, y = {data_accel['y']}g, z = {data_accel['z']}g")
                    print(f"Gyroscope data: x = {data_gyro['x']}°/s, y = {data_gyro['y']}°/s, z = {data_gyro['z']}°/s")
                    print("")

                    #set on the buzzer
                    GPIO.output(BUZZER_PIN, GPIO.HIGH)
                    print("Buzzer is on")

                    #send to mobile app the buzzer status
                    encrypted_data = encrypt_data("cmd-buzzerON".encode('utf-8'), shared_key_retreived)
                    mqttClient.publish(topicPubCommands, encrypted_data)

                    #send to thingsboard the new buzzer status
                    buzzerData = {
                        "startBuzzer": True
                    }
                    urlAttributes = "https://demo.thingsboard.io/api/v1/rdtiahf5espbzqxczttn/attributes"
                    headers = {
                        "Content-Type": "application/json"
                    }

                    response = requests.post(urlAttributes, headers=headers, json=buzzerData)

                    # get contacts id and send the message
                    contacts = retrieve_contact_ids()
                    for id in contacts:
                        asyncio.run(send_message(id))

                    if response.status_code != 200:
                        print(f"Failed to post data to thingsboard: {response.status_code}")
                        #print(response.text)

                    
            sleep(0.5)
        except Exception as e:
            #if some problems using the I2C bus, try to connect again
            sleep(0.5)
            while True:
                try:
                    sensorMPU6050= mpu6050(0x68)
                    sleep(0.5)
                    break
                except Exception as e:
                    sleep(0.5)
            sleep(0.5)

def detect_crash(data_accel):
    if abs(abs(data_accel['z'])>8):
        #only when the car is almost parallel to the groud, in the other case will be detected as rollover
        if abs(data_accel['x']) > ACCEL_CRASH_THRESHOLD or abs(data_accel['y']) > ACCEL_CRASH_THRESHOLD :
            return True
    return False

def detect_rollover(data_accel, data_gyro):

    # detects if it is turning because the force (gravity) in the Z axis change
    if abs(data_accel['z']) < ROLLOVER_Z_THRESHOLD:
        return True

    # for when the car rollover so quick
    if abs(data_gyro['x']) > GYRO_ROLLOVER_THRESHOLD or abs(data_gyro['y']) > GYRO_ROLLOVER_THRESHOLD:
        return True

    return False

async def send_message(id):
    # to send the telegram message
    global userName
    global userPhone
    global carLicense
    global carBrand
    global carModel
    global carColor
    global latitudeValue
    global longitudeValue

    current_time = datetime.now()
    formatted_time = current_time.strftime("%m/%d/%Y %H:%M")

    # create the customize message using the user and car information
    message = f"Your contact {userName} has suffered and accident with his car model {carBrand} {carModel}.\nTime of the accident: {formatted_time}\nLocation: {latitudeValue}, {longitudeValue}\n https://www.google.com/maps?q={latitudeValue},{longitudeValue}"
    try:
        bot = Bot(token=TELEGRAM_TOKEN)
        await bot.send_message(chat_id=id, text=message)
        print(f"Message sent to user ID: {id}")
    except BadRequest as e:
        print(f"Failed to send the message to the username with ID '{id}': {e}")

def collect_monitoring_data():

    global temperatureThreshold
    global automaticVentilation
    global automaticVentilationStatus
    global startBuzzer
    global accidentDetection
    global startVentilation
    global lcd
    

    # create i2c bus
    i2c = busio.I2C(board.SCL, board.SDA)


    while True:
        try:
            sensor = adafruit_si7021.SI7021(i2c)
            break

        except Exception as  e:
            print("Try again to get temp sensor connection")
            time.sleep(1)
    
        
    while True:
        try:
            
            #get data
            temperature = sensor.temperature
            humidity = sensor.relative_humidity

            print(f"Temperature: {temperature} °C")
            print(f"Humidity: {humidity} %")

            data = {
                'temperature': temperature,
                'humidity': humidity
            }
            print(f"Sending monitoring data: {data}")
            print("")

            #encript and send the data using mqtt
            data_bytes = json.dumps(data).encode('utf-8')
            encrypted_data = encrypt_data(data_bytes, shared_key_retreived)
            mqttClient.publish(topicPubData, encrypted_data)

            # publish data in thingsboard (using https requests)
            sendThingsboardData(data, "https://demo.thingsboard.io/api/v1/rdtiahf5espbzqxczttn/telemetry")

            # show values on LCD
            lcd.clear()
            lcd.write_string(f"Temp: {temperature:.1f} C")
            lcd.cursor_pos = (1, 0)  
            lcd.write_string(f"Hum: {humidity:.0f} %")

            print(f"automatic ventilation:{automaticVentilation}, temperatire:{temperature}, threshold:{temperatureThreshold}, automaticVentilationStatus:{automaticVentilationStatus}")

            if automaticVentilation:
                if temperature > temperatureThreshold:
                    if automaticVentilationStatus == False:
                        #start ventilation
                        GPIO.output(VENTILATION_PIN, GPIO.HIGH)
                        print(f"Temperature ({temperature}) exceeded the threshold ({temperatureThreshold}): Fan Started")
                        automaticVentilationStatus = True
                        encrypted_data = encrypt_data("cmd-fanON".encode('utf-8'), shared_key_retreived)
                        mqttClient.publish(topicPubCommands, encrypted_data)

                        # sent tho thingsboard when the ventilation is on
                        data = {
                            "isFanActive": True
                        }
                        urlTelemetry = "https://demo.thingsboard.io/api/v1/rdtiahf5espbzqxczttn/telemetry"
                        sendThingsboardData(data, urlTelemetry)
                else:
                    if automaticVentilationStatus:
                        if not startVentilation:
                            if automaticVentilationStatus == True:
                                #stop ventilation
                                print(f"Temperature ({temperature}) went down the threshold ({temperatureThreshold}): Fan Stoped")
                                GPIO.output(VENTILATION_PIN, GPIO.LOW)
                                encrypted_data = encrypt_data("cmd-fanOFF".encode('utf-8'), shared_key_retreived)
                                mqttClient.publish(topicPubCommands, encrypted_data)

                                # sent tho thingsboard when the ventilation is off
                                data = {
                                    "isFanActive": False
                                }
                                urlTelemetry = "https://demo.thingsboard.io/api/v1/rdtiahf5espbzqxczttn/telemetry"
                                sendThingsboardData(data, urlTelemetry)
                        else:
                            print(f"Temperature ({temperature}) went down the threshold ({temperatureThreshold}), but fun keeps avtive since it was activated manually")

            time.sleep(10)

        except Exception as e:
            time.sleep(10)

   






def encrypt_data(data, shared_key):
    iv = os.urandom(16) #get iv
    cipher = Cipher(algorithms.AES(shared_key[:16]), modes.CBC(iv), backend=default_backend())
    encryptor = cipher.encryptor()

    #add padding
    padder = padding.PKCS7(algorithms.AES.block_size).padder()
    padded_data = padder.update(data) + padder.finalize()
    
    #encrypt
    encrypted_data = encryptor.update(padded_data) + encryptor.finalize()
    
    return iv + encrypted_data #join Iv and encrypted data

def decrypt_data(encrypted_data, shared_key):
    #separate the iv and the data
    iv = encrypted_data[:16]
    actual_encrypted_data = encrypted_data[16:] 
    
    cipher = Cipher(algorithms.AES(shared_key[:16]), modes.CBC(iv), backend=default_backend())
    decryptor = cipher.decryptor()

    #decrypt
    decrypted_padded_data = decryptor.update(actual_encrypted_data) + decryptor.finalize()

    #undo the padding
    unpadder = padding.PKCS7(algorithms.AES.block_size).unpadder()
    decrypted_data = unpadder.update(decrypted_padded_data) + unpadder.finalize()

    return decrypted_data


def sendThingsboardData(data, urlTelemetry):
    headers = {
        "Content-Type": "application/json"
    }

    response = requests.post(urlTelemetry, headers=headers, json=data)

    if response.status_code != 200:
        print(f"Failed to post data to thingsboard: {response.status_code}")
        print(response.text)
        
        

def getThingsboardAtributes():
    # get attributes from thingsbpard for the beguining of the eceution to get the current state
    global temperatureThreshold
    global automaticVentilation
    global automaticVentilationStatus
    global startBuzzer
    global accidentDetection
    global startVentilation

    url_attribute = "https://demo.thingsboard.io/api/v1/rdtiahf5espbzqxczttn/attributes"
    bearer_token = "Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJtYXJpb2xvcGV6Y2VhMTVAaG90bWFpbC5jb20iLCJ1c2VySWQiOiIwNWYzMTliMC0yZDU2LTExZWYtOTIyOS1mM2FhNTcwNjgwZmIiLCJzY29wZXMiOlsiVEVOQU5UX0FETUlOIl0sInNlc3Npb25JZCI6ImYwZGFlYTM1LTBiYjYtNGFlYy1iZjIyLWQ1NDRlMGViODNhZSIsImV4cCI6MTcyMDUwMzM1MywiaXNzIjoidGhpbmdzYm9hcmQuaW8iLCJpYXQiOjE3MTg3MDMzNTMsImZpcnN0TmFtZSI6Ik1hcmlvIiwibGFzdE5hbWUiOiJMb3BleiIsImVuYWJsZWQiOnRydWUsInByaXZhY3lQb2xpY3lBY2NlcHRlZCI6dHJ1ZSwiaXNQdWJsaWMiOmZhbHNlLCJ0ZW5hbnRJZCI6IjA0OWFiNWEwLTJkNTYtMTFlZi05MjI5LWYzYWE1NzA2ODBmYiIsImN1c3RvbWVySWQiOiIxMzgxNDAwMC0xZGQyLTExYjItODA4MC04MDgwODA4MDgwODAifQ.-itGj51w4OC5JcrSWxbULhRrfpplyI2MtVpV6gXTxF31kjSfkfmuvtnT5xqO-cilfJ-bEaM4z6QJehZ4-I6EHA"
    headersGet = {
        "Content-Type": "application/json",
        "Authorization": bearer_token
    }
	#get request
    response = requests.get(url_attribute, headers=headersGet)
    print(response)
    if response.status_code == 200:
        data = response.json()
        # get and stoe in the variables data from the json
        shared = data.get('shared', {})
        temperatureThreshold = shared.get('temperatureThreshold')
        automaticVentilation = shared.get('automaticVentilation')
        startVentilation= shared.get('startVentilation')
        startBuzzer = shared.get('startBuzzer')
        accidentDetection = shared.get('accidentDetection')

    
        print("")
        print(f"Temperature Threshold: {temperatureThreshold}")
        print(f"Automatic Ventilation: {automaticVentilation}")
        print(f"Start Ventilation: {startVentilation}")
        print(f"Start Buzzer: {startBuzzer}")
        print(f"Accident Detection: {accidentDetection}")
        print("")

        if startBuzzer:
            GPIO.output(BUZZER_PIN, GPIO.HIGH)
        else:
            GPIO.output(BUZZER_PIN, GPIO.LOW)

        if startVentilation:
            GPIO.output(VENTILATION_PIN, GPIO.HIGH)
        else:
            if not automaticVentilation:
                GPIO.output(VENTILATION_PIN, GPIO.LOW)
            


    


    else:
        print(f"Failed to get data, status code: {response.status_code}")
        print(response.text)
     


def store_contacts(json_array_string, filename="contacts.csv"):
    
    contacts = json.loads(json_array_string)

    # cvs columns
    fieldnames = ["id", "name"]

    with open(filename, mode="w", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames)
        writer.writeheader()

        #one contact for each row
        for contact in contacts:
            writer.writerow(contact)


def retrieve_contact_ids(filename="contacts.csv"):
    contact_ids = []

    #open cvs file
    with open(filename, mode="r", newline="") as file:
        reader = csv.DictReader(file)

        #get the id value for each row
        for row in reader:
            contact_ids.append(row["id"])

    return contact_ids


def setUpPins():
    # gpio setup
    GPIO.setmode(GPIO.BCM)
    GPIO.setup(BUZZER_PIN, GPIO.OUT)
    GPIO.setup(VENTILATION_PIN, GPIO.OUT)
    GPIO.setup(LIGHTS_PIN, GPIO.OUT)


if  not os.path.isfile('shared_key.bin'):
    #if no shared key already generated first is needed the pairing and generating
    print("Shared_key.bin does not exists")
    print("Starting paring process")


    script_path = './tfmBluetooth.py'

    # call function for the pairing and key generation process
    tfmBluetooth.initBluetooth()
    #at this point the key is already created and stored

    while not os.path.isfile('shared_key.bin'):
        time.sleep(1)

    print('Bluetooth script has finished')


mqttClient = mqtt.Client(clean_session=True)

#get key from the file
print("Shared_key.bin already created")
with open("shared_key.bin", "rb") as key_file:
    shared_key_retreived = key_file.read()
print("Shared key:", shared_key_retreived.hex())


setUpPins() 

# get attributes values that defines the system configuration
getThingsboardAtributes()

# connect to mqtt broker and subscribe to topics
connectMqtt()


# start the data collection threads
accelerometer_thread = threading.Thread(target=collect_accelerometer_data)
monitoring_data_thread = threading.Thread(target=collect_monitoring_data)
location_thread = threading.Thread(target=collect_location_data)


accelerometer_thread.start()
monitoring_data_thread.start()
location_thread.start()

# eep the main thread alive
try:
    while True:
        time.sleep(1)
except KeyboardInterrupt:
    print("Exiting...")
    GPIO.output(BUZZER_PIN, GPIO.LOW)
    GPIO.output(VENTILATION_PIN, GPIO.LOW)
    mqttClient.loop_stop()
    mqttClient.disconnect()
    lcd.clear()

    
    