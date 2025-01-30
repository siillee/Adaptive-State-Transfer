import os

def average_bandwidth_to_faulty(file_paths, test_case, folder_name):
    x_totals = []
    total_sum_x = 0
    total_count_x = 0

    try:
        # Read each file and accumulate x values by line index
        for file_path in file_paths:
            with open(file_path, 'r') as file:
                file.readline()
                for idx, line in enumerate(file):
                    parts = line.split(',')
                    if len(parts) >= 2:
                        try:
                            x_value = float(parts[1].strip())
                            # Ensure x_totals has an entry for this line index
                            if len(x_totals) <= idx:
                                x_totals.append([0, 0])  # [sum_x, count]
                            # Add x value to the corresponding line index
                            x_totals[idx][0] += x_value
                            x_totals[idx][1] += 1
                            # Add to total for overall average calculation
                            total_sum_x += x_value
                            total_count_x += 1
                        except ValueError:
                            print(f"Skipping line in {file_path} due to non-numeric x value: {line.strip()}")
                    else:
                        print(f"Skipping malformed line in {file_path}: {line.strip()}")

        # Calculate and print average of x values for each line index
        with open("../test_results/bandwidth_results.txt", 'a') as result_file:
            for idx, (sum_x, count) in enumerate(x_totals):
                if count > 0:
                    average_x = sum_x / count
                    # result_file.write(f"Line {idx + 1} - Average x: {average_x}\n")
                    print(f"Line {idx + 1} - Average x: {average_x}")
                else:
                    print(f"Line {idx + 1} has no valid x values across the files.")

            # Calculate and write overall average of all x values
            if total_count_x > 0:
                overall_average_x = total_sum_x / total_count_x
                result_file.write(f"Average MBs (per replica) sent to faulty replica during the {folder_name} state transfer in the {test_case} case is: {overall_average_x / 1024 / 1024}MB\n")
                print(f"\nOverall Average x: {overall_average_x}")
            else:
                print("No valid x values found across all files.")

    except FileNotFoundError as e:
        print(f"File not found: {e.filename}")
    except Exception as e:
        print(f"An error occurred: {e}")

def average_bandwidth_consumption(file_paths, test_case, folder_name):
    xyz_totals = []
    total_sum_xyz = 0
    total_count_xyz = 0

    try:
        # Read each file and accumulate x+y+z sums by line index
        for file_path in file_paths:
            with open(file_path, 'r') as file:
                file.readline()
                for idx, line in enumerate(file):
                    parts = line.split(',')
                    if len(parts) >= 4:
                        try:
                            # Convert x, y, z values to floats and sum them
                            x_value = float(parts[1].strip())
                            y_value = float(parts[2].strip())
                            z_value = float(parts[3].strip())
                            xyz_sum = x_value + y_value + z_value

                            # Ensure xyz_totals has an entry for this line index
                            if len(xyz_totals) <= idx:
                                xyz_totals.append([0, 0])  # [sum_xyz, count]
                            # Add xyz_sum to the corresponding line index
                            xyz_totals[idx][0] += xyz_sum
                            xyz_totals[idx][1] += 1
                            # Add to total for overall average calculation
                            total_sum_xyz += xyz_sum
                            total_count_xyz += 1
                        except ValueError:
                            print(f"Skipping line in {file_path} due to non-numeric value: {line.strip()}")
                    else:
                        print(f"Skipping malformed line in {file_path}: {line.strip()}")

        # Calculate and print average of x+y+z values for each line index
        with open("../test_results/bandwidth_results.txt", 'a') as result_file:
            for idx, (sum_xyz, count) in enumerate(xyz_totals):
                if count > 0:
                    average_xyz = sum_xyz / count
                    # result_file.write(f"Line {idx + 1} - Average x+y+z: {average_xyz}\n")
                    print(f"Line {idx + 1} - Average x+y+z: {average_xyz}")
                else:
                    print(f"Line {idx + 1} has no valid x, y, z values across the files.")

            # Calculate and write overall average of all x+y+z values
            if total_count_xyz > 0:
                overall_average_xyz = total_sum_xyz / total_count_xyz
                result_file.write(f"Average MBs (per replica) consumed during the {folder_name} state transfer in the {test_case} case is: {overall_average_xyz / 1024 / 1024}MB\n")
                print(f"\nOverall Average x+y+z: {overall_average_xyz}")
            else:
                print("No valid x, y, z values found across all files.")

    except FileNotFoundError as e:
        print(f"File not found: {e.filename}")
    except Exception as e:
        print(f"An error occurred: {e}")


def read_csv_from_txt(folder_name):
    # Check if folder exists
    if not os.path.isdir(folder_name):
        print("The specified folder does not exist.")
        return
    
    average_bandwidth_to_faulty([
        f"{folder_name}/replica1/all_healthy.txt",
        f"{folder_name}/replica3/all_healthy.txt", 
        f"{folder_name}/replica4/all_healthy.txt"], "all_healthy", folder_name.split('/')[-1])

    average_bandwidth_to_faulty([
        f"{folder_name}/replica1/healthy_byzantine.txt",
        f"{folder_name}/replica3/healthy_byzantine.txt", 
        f"{folder_name}/replica4/healthy_byzantine.txt"], "healthy_byzantine", folder_name.split('/')[-1])

    average_bandwidth_consumption([
        f"{folder_name}/replica1/all_healthy.txt",
        f"{folder_name}/replica1/all_healthy.txt",
        f"{folder_name}/replica3/all_healthy.txt", 
        f"{folder_name}/replica4/all_healthy.txt"], "all_healthy", folder_name.split('/')[-1])

    average_bandwidth_consumption([
        f"{folder_name}/replica1/healthy_byzantine.txt",
        f"{folder_name}/replica2/healthy_byzantine.txt",
        f"{folder_name}/replica3/healthy_byzantine.txt", 
        f"{folder_name}/replica4/healthy_byzantine.txt"], "healthy_byzantine", folder_name.split('/')[-1])


# Request folder input from the user
folder_name = input("Enter the folder name: ")
read_csv_from_txt(folder_name)