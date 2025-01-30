#!/bin/bash

# INTERFACE="enp39s0"
# IFB_INTERFACE="ifb0"

# # Clear existing qdisc rules
# echo "Clearing existing qdisc rules on $INTERFACE and $IFB_INTERFACE..."
# sudo tc qdisc del dev $INTERFACE ingress 2>/dev/null
# sudo tc qdisc del dev $IFB_INTERFACE root 2>/dev/null

# Interface name
INTERFACE="enp39s0"
IFB_INTERFACE="ifb0"

echo "Clearing traffic control rules on $INTERFACE and $IFB_INTERFACE..."

# Delete ingress qdisc from the main interface
sudo tc qdisc del dev $INTERFACE ingress 2>/dev/null || echo "No ingress qdisc to delete on $INTERFACE."

# Delete root qdisc from the IFB interface
sudo tc qdisc del dev $IFB_INTERFACE root 2>/dev/null || echo "No root qdisc to delete on $IFB_INTERFACE."

# Bring down and delete the IFB interface
sudo ip link set $IFB_INTERFACE down 2>/dev/null || echo "Failed to bring down $IFB_INTERFACE (or it's already down)."
sudo ip link delete $IFB_INTERFACE type ifb 2>/dev/null || echo "Failed to delete $IFB_INTERFACE (or it doesn't exist)."

echo "Traffic control rules cleared. $INTERFACE and $IFB_INTERFACE are now reset to default."

# Verify the configuration
echo "Current configuration:"
tc qdisc show dev $INTERFACE
