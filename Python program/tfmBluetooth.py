import os
import glob
import time
import RPi.GPIO as GPIO
import subprocess as sp
import random
from RPLCD.gpio import CharLCD
from time import sleep
from bluetooth import *
from cryptography.hazmat.primitives.asymmetric import dh
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.backends import default_backend
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.primitives import padding



server_sock =None
port = None
uuid = None
lcd = None



def initBluetooth():
    global server_sock
    global port 
    global uuid
    global lcd

    # set up bluetooth
    server_sock = BluetoothSocket(RFCOMM)
    server_sock.bind(("", PORT_ANY))
    server_sock.listen(1)

    port = server_sock.getsockname()[1]

    uuid = "94f39d29-7d6d-437d-973b-fba39e49d4ee"

    advertise_service(server_sock, "AquaPiServer",
                service_id=uuid,
                service_classes=[uuid, SERIAL_PORT_CLASS],
                profiles=[SERIAL_PORT_PROFILE])


    lcd = CharLCD(
        numbering_mode=GPIO.BCM,
        cols=16, rows=2,  # Change these values if you have a different size LCD
        pin_rs=7, pin_e=8,
        pins_data=[13, 6, 5, 11])


    while(not stablishConnection()):
        print("Error when creating the shared key")
        print("Try again")



def isConnected(mac):
    stdoutdata = sp.getoutput("hcitool con")

    if mac in stdoutdata.split():
        return True
    else:
        return False

def readBluetoothBuffer(client_sock, timeout=120):
    start_receiving = False
    received_string = ""
    start_time = time.time()

    while (time.time() - start_time) < timeout:
        start_receiving = True
        data = client_sock.recv(1024)
        if len(data) == 0:
            break
        received_string=data.decode('utf-8').strip()
        print(f"received: '{received_string}'")
        break
        
        
    if start_receiving:
        received_string = received_string.strip()

    return received_string


def stablishConnection():
    print("Waiting for connection on RFCOMM channel %d" % port)

    client_sock, client_info = server_sock.accept()
    mac_address = client_info[0]
    print("Accepted connection from ", client_info)
    client_sock.send("cmd-number?\n")
    connection = 0
    numberMatched = False

    

    while True:
        # generate a random value and show it in the display
        pairNumber = random.randint(100000, 999999)
        lcd.clear()
        lcd.write_string(f"Code:\n {pairNumber}")

        
        receivedString = readBluetoothBuffer(client_sock)

        
        if(isConnected(mac_address)):
        
            if(receivedString == ""):
                # when no code was introdcued
                client_sock.send("cmd-disconnect\n")
                print("No number introduced")
                connection=0
                break

            elif(receivedString=="msg-disconnected"):
                # when the phone sent a disconection command
                connection=0
                break

            else:
                print("Received a non-empty string")
                try:
                    received_int = int(receivedString)
                    if(pairNumber == received_int):
                        #code matched 
                        print("Code matched")
                        client_sock.send("msg-codeMatched\n")
                        numberMatched = True
                        break
                    else:
                        #code do not matched
                        print("Code NOT matched")
                        client_sock.send("msg-codeNotMatched\n")


                except ValueError:
                    print(f"Error: '{receivedString}' is not a valid integer.")
                    client_sock.send("cmd-disconnect\n") #tell the phone that disconnect the devices
                    connection=0
                    break

        else:
            #When connection was lost during the code communication
                print("Lost communication")
                connection=0
                break
        
    
    if(connection):
        while (not isConnected(mac_address)): #wait till some device connect to the module
            time.sleep(0.5)
        print("Real disconnection")
    elif(numberMatched):
        #means that the connection has been strablished
        print("START THE KEY GENERATION")
        if(generate_shared_key(client_sock)):
            return True
        else:
            return False

        
    else:
        #close bluetooth socket
        client_sock.send("cmd-disconnect\n")
        print("disconnected")
        client_sock.close()
        server_sock.close()
        return False




def generate_shared_key(client_sock):
    # get DH parameter (p, g) that is stored in a file
    with open("dh_parameters.pem", "rb") as param_file:
        param_bytes = param_file.read()

    # Load the parameters from the serialized PEM data
    parameters = serialization.load_pem_parameters(param_bytes, backend=default_backend())
    p_value = parameters.parameter_numbers().p
    g_value = parameters.parameter_numbers().g
    # Print the prime value
    print("Prime (p) value:", p_value)
    print("G (g) value:", g_value)
    
    # generate a private key
    private_key = parameters.generate_private_key()

    # generat ethe public key using the private
    public_key = private_key.public_key()
    print(f"Public key: '{public_key}'")


    print("Serializing key")
    pem_public_key = public_key.public_bytes(encoding=serialization.Encoding.PEM,
                                             format=serialization.PublicFormat.SubjectPublicKeyInfo)
    
    if(isConnected):
        client_sock.send(pem_public_key)
        print("Sent public key to client.")
        print(pem_public_key)

        # receive client's public key
        received_bytes = client_sock.recv(1024) 
        print(f"Key received:")
        print(received_bytes)
        # load client's public key from received bytes
        client_public_key = serialization.load_der_public_key(received_bytes, backend=default_backend())


        # generate the shared key
        shared_key = private_key.exchange(client_public_key)
        print(f"\nShared key in Hexadecimal:\n{shared_key.hex()}")
        print(f"Shared key:\n{shared_key}")

        # store the shared key in a file
        with open("shared_key.bin", "wb") as key_file:
            key_file.write(shared_key)

        print("Shared key stored.")
        
        print("Bluetoot disconnected after key creation")
        client_sock.close()
        server_sock.close()

        return True
    else:
        return False

