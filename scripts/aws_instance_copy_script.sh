#!/bin/bash

# JAR_FILE_PATH="../target/thesis_repo-main_bw_testing-jar-with-dependencies.jar"
JAR_FILE_PATH="../target/thesis_repo-new_algorithm-jar-with-dependencies.jar"
# JAR_FILE_PATH="../target/thesis_repo-main_state_transfer-jar-with-dependencies.jar"
CONFIG_FILE_PATH_1="../configs/config_bw-testing_aws"
CONFIG_FILE_PATH_2="../configs/config_bw-testing_aws_2"
CONFIG_FILE_PATH_3="../configs/config_aws"
START_SCRIPT_PATH="aws_instance_start_script.sh"

DEST_PATH="/home/ubuntu/"

# List of instance IPs
#                                                     "3.72.113.128" "18.195.81.180"
IPS=("54.93.106.122" "3.121.233.128" "54.93.249.44")

# For loop to copy the file to each instance
for IP in "${IPS[@]}"; do
  echo "Copying file to $IP..."
  scp "$JAR_FILE_PATH" ubuntu@"$IP":"$DEST_PATH"
  scp "$CONFIG_FILE_PATH_1" ubuntu@"$IP":"${DEST_PATH}configs" 
  scp "$CONFIG_FILE_PATH_2" ubuntu@"$IP":"${DEST_PATH}configs"
  scp "$CONFIG_FILE_PATH_3" ubuntu@"$IP":"${DEST_PATH}configs"
  scp "$START_SCRIPT_PATH" ubuntu@"$IP":${DEST_PATH}
  if [ $? -eq 0 ]; then
    echo "File successfully copied to $IP"
  else
    echo "Failed to copy file to $IP"
  fi
done
