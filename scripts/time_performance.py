import os
import sys
import matplotlib.pyplot as plt
import numpy as np
from scipy.interpolate import interp1d

def average_time(folder_name, file_path, result_file="../state_transfer-test_results/time_results.txt"):
    numbers = []
    
    try:
        with open(file_path, 'r') as file:
            for line in file:

                if '=' in line:
                    continue

                # parts = line.split(',')
                # if int(parts[0].strip()) == 10:
                #     continue

                parts = line.split(' ')
                number = float(parts[2].strip())

                # number = float(parts[1].strip())

                numbers.append(number)
        
        # Calculate the average if there are any numbers
        if numbers:
            average = sum(numbers) / len(numbers)
            print(f"Average of all numbers: {average}")
            
            # Append the result to the result file
            # with open(result_file, 'a') as result:
                # result.write(f"Average time of 2GB transfer with new algo in the scenario 2 case: {average}")
                # result.write(f"Average time it takes to complete {folder_name.split('/')[-1]} state transfer for " + 
                #     f"100MB epochs, a 10 epoch lag, and in the {os.path.splitext(file_path.split('/')[-1])[0]} case is: {average} seconds\n")
            print(f"Result appended to {result_file}.")
        else:
            print("No valid numbers found in the file.")

    except FileNotFoundError:
        print("The specified file does not exist.")
    except Exception as e:
        print(f"An error occurred: {e}")


def read_csv_from_txt(file_path):
    # Check if folder exists
    # if not os.path.isdir(folder_name):
    #     print("The specified folder does not exist.")
    #     return
    
    # # Find .txt files in the folder
    # txt_files = [f for f in os.listdir(folder_name) if f.endswith('.txt')]
    
    # if not txt_files:
    #     print("No .txt files found in the specified folder.")
    #     return
    
    # # Process each .txt file
    # for txt_file in txt_files:
    #     file_path = os.path.join(folder_name, txt_file)
    #     print(f"Reading file: {file_path}")
        
    with open(file_path, 'r') as file:
        average_time("folder_name", file_path)
            # for line in file:
            #     # Split line by comma and strip whitespace from each value
            #     values = [value.strip() for value in line.split(',')]
            #     print("Values:", values)



read_csv_from_txt(sys.argv[1])


# Specify the path to the file
# file_path = input("Enter the path to the .txt file: ")
# average_numbers(file_path)