import os
import sys
import matplotlib.pyplot as plt
import numpy as np
from scipy.interpolate import interp1d

data_size_in_bits = 16777216000

def read_from_txt_to_array(file_path):

    numbers = []
    
    try:
        with open(file_path, 'r') as file:
            for line in file:
                parts = line.split(' ')
                
                number = data_size_in_bits / float(parts[2].strip()) / 1000000
                # number = float(parts[2].strip())
                numbers.append(number)
    except FileNotFoundError:
        print("The specified file does not exist.")
    except Exception as e:
        print(f"An error occurred: {e}")

    return numbers


# TODO: CHANGE THIS STUPID THING by changing the txt files to something at least more uniform, if not unifrom
def read_from_txt_to_array_2(file_path):

    numbers = []
    
    try:
        with open(file_path, 'r') as file:
            for line in file:
                parts = line.split(' ')
                
                number = data_size_in_bits / float(parts[0].strip()) / 1000000
                # number = float(parts[0].strip())
                numbers.append(number)
    except FileNotFoundError:
        print("The specified file does not exist.")
    except Exception as e:
        print(f"An error occurred: {e}")

    return numbers


def data_to_graph(file_path1, file_path2, file_path3):

    theoretical_max_throughput = 1000
    theoretical_minimum_time = 16.777216
    #Average of scenario_1.txt file from aws_results_3
    # practical_minimum_time = 17.4174744049
    practical_max_throughput = read_from_txt_to_array(file_path3)

    data_new_algo = read_from_txt_to_array(file_path1)
    data_static_allocation = read_from_txt_to_array_2(file_path2)

    # Specific values for horizontal lines
    y_line1 = theoretical_max_throughput
    # y_line2 = practical_minimum_time


    # x = np.arange(len(data_new_algo))  # Original x values
    # interp_func = interp1d(x, data_new_algo, kind='cubic')  # Cubic interpolation
    # x_new = np.linspace(0, len(data_new_algo) - 1, 68)    # New x values for smooth curve
    # data_new_algo_smooth = interp_func(x_new)


    # Create the line graph
    plt.figure(figsize=(10, 6))
    plt.plot(data_new_algo, label="Version of New Algorithm", marker='o', linestyle='-', color='blue')
    plt.plot(data_static_allocation, label="Static Allocation", marker='o',linestyle='-', color='orange')
    plt.plot(practical_max_throughput, label="Practical Maximum", marker='o',linestyle='-', color='purple')

    # Add horizontal lines
    plt.axhline(y=y_line1, color='green', linestyle='--', label=f'Theoretical Maximum')
    # plt.axhline(y=y_line2, color='green', linestyle='--', label=f'Practical Minimum')

    # Add labels, title, and legend
    plt.title("Kernel-based BW Allocation AST vs Static Allocation of BW", fontsize=16)
    plt.xlabel("Time", fontsize=12)
    plt.ylabel("Throughput (in Mbps)", fontsize=12)
    plt.legend(loc='center left', bbox_to_anchor=(1, 0.5), fontsize=17)
    plt.tight_layout(pad=3)
    plt.grid(True, linestyle='--', alpha=0.7)

    plt.savefig('result_graphs/graph_just_for_legend.png')

    # Display the graph
    plt.show()

def data_to_graph_scenario_3(file_path1, file_path2):

    data_receiver_1 = read_from_txt_to_array(file_path1)
    data_receiver_2 = read_from_txt_to_array(file_path2)

    # x = np.arange(len(data_new_algo))  # Original x values
    # interp_func = interp1d(x, data_new_algo, kind='cubic')  # Cubic interpolation
    # x_new = np.linspace(0, len(data_new_algo) - 1, 68)    # New x values for smooth curve
    # data_new_algo_smooth = interp_func(x_new)

    # Create the line graph
    plt.figure(figsize=(10, 6))
    plt.plot(data_receiver_1, label="Faster Replica", marker='o', linestyle='-', color='blue')
    plt.plot(data_receiver_2, label="Slower Replica", marker='o', linestyle='-', color='firebrick')
    # plt.plot(data_static_allocation, label="Static Allocation", marker='o',linestyle='-', color='orange')

    # Add labels, title, and legend
    plt.title("Evolution of Two Replicas Recovering - Flipped", fontsize=16)
    plt.xlabel("Time", fontsize=12)
    plt.ylabel("Throughput (in Mbps)", fontsize=12)
    plt.legend(loc='lower right')
    # plt.legend(loc='center')
    # plt.legend(loc='center left', bbox_to_anchor=(1, 0.5), fontsize=17)
    # plt.tight_layout(pad=3)
    plt.grid(True, linestyle='--', alpha=0.7)

    plt.savefig('result_graphs/new_algo_scenario_3_flipped.png')

    # Display the graph
    plt.show()


def generate_bar_plot(title="Bar Plot", y_label="State Transfer Time (in seconds)", colors=None):

    # Values for first bar plot
    #       Old:       26.91         33.6
    # values = [25.12, 25.77, 28.78, 32]

    # Values from the final, better version of the algo with the sliding window
    # values = [20.59, 20.81, 21.86, 28.78]

    # Values for second bar plot; averages from aws_results_4 and aws_results_3 in order: scenario_1_new_algo.txt, scenario_1_new_algo_new_version.txt, scenario_1.txt, scenario_1_new_algo_htb_prio.txt
    values = [18.36, 18.37, 18.31, 17.43]

    labels = ["Uniform BW Alloc", "Proportional BW Alloc", "Kernel-based BW Alloc", "Static Allocation"]

    if len(values) != 4 or len(labels) != 4:
        raise ValueError("The 'values' and 'labels' lists must each contain exactly 4 elements.")

    # Default colors if none provided
    if colors is None:
        colors = ['blue', 'blue', 'blue', 'orange']

    # Create the bar plot
    plt.figure(figsize=(8, 6))
    plt.bar(labels, values, color=colors, width=0.4)

    # Add labels and title
    plt.title(title, fontsize=16)
    plt.ylabel(y_label, fontsize=12)
    plt.xlabel("Algorithm Version", fontsize=12)
    plt.grid(axis='y', linestyle='--', alpha=0.7)

    plt.savefig('result_graphs/solutions_comparison_bar_plot_no_noisy.png')

    # Display the plot
    plt.show()


# data_to_graph(sys.argv[1], sys.argv[2], sys.argv[3])
# data_to_graph_scenario_3(sys.argv[1], sys.argv[2])
# generate_bar_plot("AST Performance during 2GB State Transfer")

def generate_grouped_bar_plot(values, labels, group_labels, title="Grouped Bar Plot", y_label="Values", colors=None):

    # Default colors if none provided
    if colors is None:
        colors = ['blue', 'firebrick']

    # Bar positions
    n_groups = len(values)
    bar_width = 0.3
    index = np.arange(n_groups)

    # Separate values for the two bars in each group
    bar1 = [pair[0] for pair in values]
    bar2 = [pair[1] for pair in values]

    # Create the bar plot
    plt.figure(figsize=(10, 6))
    plt.bar(index, bar1, bar_width, label=labels[0], color=colors[0])
    plt.bar(index + bar_width, bar2, bar_width, label=labels[1], color=colors[1])

    # Add labels and title
    plt.title(title, fontsize=16)
    plt.ylabel(y_label, fontsize=12)
    plt.xticks(index + bar_width / 2, group_labels)
    plt.legend(loc='upper left') 
    plt.grid(axis='y', linestyle='--', alpha=0.7)
    plt.ylim(0, 40)

    plt.savefig('result_graphs/scenario_3_bar_plot_flipped.png')

    # Display the plot
    plt.show()


# Average from scenario_3_new_algo_receiver_1.txt : 28.55           new: 25.43      better results version: 25.75
# Average from scenario_3_new_algo_receiver_1_flipped.txt : 28.9    new: 25.33      better results version: 26.07
# Average from scenario_3_new_algo_receiver_2.txt : 28.48           new: 26.98      better results version: 26.22
# Average from scenario_3_new_algo_receiver_2_flipped.txt : 28.95   new: 27.02      better results version: 26.77
# Average from scenario_3_htb_static_allocation_receiver_1.txt : 25.63
# Average from scenario_3_htb_static_allocation_receiver_1_flipped.txt : 28.92
# Average from scenario_3_htb_static_allocation_receiver_2.txt : 32.8
# Average from scenario_3_htb_static_allocation_receiver_2_flipped.txt : 36.16

# The regular case
# values = [[25.75, 26.22], [25.63, 32.8]]
# group_labels = ["Adaptive State Transfer", "Static Allocation"]

# The flipped case
values = [[26.07, 26.77], [28.92, 36.16]]
group_labels = ["Adaptive State Transfer - Flipped", "Static Allocation - Flipped"]

labels = ["Faster Replica", "Slower Replica"]
generate_grouped_bar_plot(values, labels, group_labels, title="Two Replicas Recovering - Flipped", y_label="State Transfer Time (in seconds)")

