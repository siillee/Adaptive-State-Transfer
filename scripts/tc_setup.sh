#!/bin/bash

# Clear any existing tc rules
sudo tc qdisc del dev lo root 2>/dev/null

# Create a root qdisc using HTB
sudo tc qdisc add dev lo root handle 1: htb default 30

# Class 1: netem
sudo tc class add dev lo parent 1: classid 1:1 htb rate 700mbit ceil 700mbit
sudo tc qdisc add dev lo parent 1:1 handle 10: netem delay 20ms

# Class 2: tbf
sudo tc qdisc add dev lo parent 10: handle 20: tbf rate 700mbit burst 32kbit latency 400ms

# Class 3: netem
sudo tc class add dev lo parent 1: classid 1:3 htb rate 70mbit ceil 70mbit
sudo tc qdisc add dev lo parent 1:3 handle 30: netem delay 250ms

# Class 4: tbf
sudo tc qdisc add dev lo parent 30: handle 40: tbf rate 70mbit burst 32kbit latency 400ms

# Clear any existing filters
sudo tc filter del dev lo protocol ip parent 1: prio 1 2>/dev/null

# Add filters to direct traffic
sudo tc filter add dev lo protocol ip parent 1: prio 1 u32 match ip sport 20001 0xffff flowid 1:1
# sudo tc filter add dev lo protocol ip parent 1: prio 1 u32 match ip sport 20005 0xffff flowid 1:1
sudo tc filter add dev lo protocol ip parent 1: prio 1 u32 match ip sport 20003 0xffff flowid 1:3
sudo tc filter add dev lo protocol ip parent 1: prio 1 u32 match ip sport 20004 0xffff flowid 1:3

# Check the setup
sudo tc -s qdisc show dev lo
sudo tc -s class show dev lo

echo "Traffic control setup complete."
