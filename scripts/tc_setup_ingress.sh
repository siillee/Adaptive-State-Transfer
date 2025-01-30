#!/bin/bash

# # Variables
# INTERFACE="enp39s0"
# IFB_INTERFACE="ifb0"
# RATE="700mbit"
# BURST="32kbit"
# LATENCY="400ms"

# # Load IFB module
# echo "Loading IFB module..."
# sudo modprobe ifb || { echo "Failed to load IFB module"; exit 1; }

# # Create IFB device
# echo "Creating IFB device..."
# sudo ip link add $IFB_INTERFACE type ifb
# sudo ip link set $IFB_INTERFACE up

# # Clear existing qdisc rules
# echo "Clearing existing qdisc rules on $INTERFACE and $IFB_INTERFACE..."
# sudo tc qdisc del dev $INTERFACE ingress 2>/dev/null
# sudo tc qdisc del dev $IFB_INTERFACE root 2>/dev/null

# # Add ingress qdisc and redirect to IFB
# echo "Redirecting incoming traffic from $INTERFACE to $IFB_INTERFACE..."
# sudo tc qdisc add dev $INTERFACE ingress
# sudo tc filter add dev $INTERFACE parent ffff: protocol ip u32 match u32 0 0 action mirred egress redirect dev $IFB_INTERFACE

# # Apply bandwidth limit on IFB
# echo "Applying bandwidth limit on $IFB_INTERFACE..."
# sudo tc qdisc add dev $IFB_INTERFACE root tbf rate $RATE burst $BURST latency $LATENCY

# # Verify the configuration
# echo "Verifying the configuration..."
# tc -s qdisc show dev $INTERFACE
# tc -s qdisc show dev $IFB_INTERFACE

# echo "Incoming bandwidth limit of $RATE applied to $INTERFACE."



# # Variables
# INTERFACE="enp39s0"         # The physical interface
# IFB_INTERFACE="ifb0"        # The IFB virtual interface
# RATE="700mbit"              # Bandwidth rate limit
# BURST="32kbit"              # Burst size
# LATENCY="400ms"             # Latency

# # Function to handle errors
# error_exit() {
#     echo "$1" >&2
#     exit 1
# }

# # Load IFB module
# echo "Loading IFB module..."
# sudo modprobe ifb || error_exit "Failed to load IFB module."

# # Create IFB device if not exists
# if ! ip link show $IFB_INTERFACE &>/dev/null; then
#     echo "Creating IFB device..."
#     sudo ip link add $IFB_INTERFACE type ifb || error_exit "Failed to create IFB device."
# fi

# # Bring up the IFB interface
# echo "Setting $IFB_INTERFACE up..."
# sudo ip link set $IFB_INTERFACE up || error_exit "Failed to bring up $IFB_INTERFACE."

# # Clear existing qdisc rules
# echo "Clearing existing qdisc rules..."
# sudo tc qdisc del dev $INTERFACE ingress 2>/dev/null
# sudo tc qdisc del dev $IFB_INTERFACE root 2>/dev/null

# # Add ingress qdisc and redirect to IFB
# echo "Redirecting incoming traffic from $INTERFACE to $IFB_INTERFACE..."
# sudo tc qdisc add dev $INTERFACE ingress || error_exit "Failed to add ingress qdisc to $INTERFACE."
# sudo tc filter add dev $INTERFACE parent ffff: protocol ip u32 match u32 0 0 action mirred egress redirect dev $IFB_INTERFACE || error_exit "Failed to redirect traffic to $IFB_INTERFACE."

# # Apply bandwidth limit on IFB
# echo "Applying bandwidth limit on $IFB_INTERFACE..."
# sudo tc qdisc add dev $IFB_INTERFACE root tbf rate $RATE burst $BURST latency $LATENCY || error_exit "Failed to apply bandwidth limit on $IFB_INTERFACE."

# # Verify the configuration
# # echo "Verifying the configuration..."
# # sudo tc -s qdisc show dev $INTERFACE || error_exit "Failed to verify configuration on $INTERFACE."
# # sudo tc -s qdisc show dev $IFB_INTERFACE || error_exit "Failed to verify configuration on $IFB_INTERFACE."

# echo "Incoming bandwidth limit of $RATE applied to $INTERFACE."

# Interface name
INTERFACE="enp39s0"
IFB_INTERFACE="ifb0"

# Bandwidth limit
RATE="700mbit"
BURST="256kbit"
LATENCY="400ms"

echo "Setting up traffic control on $INTERFACE to limit incoming traffic to $RATE..."

# Create IFB device if not exists
if ! ip link show $IFB_INTERFACE &>/dev/null; then
    echo "Creating IFB device..."
    sudo ip link add $IFB_INTERFACE type ifb || error_exit "Failed to create IFB device."
fi

# Load the ifb module
sudo modprobe ifb || { echo "Failed to load ifb module. Exiting."; exit 1; }

# Bring up the ifb interface
sudo ip link set $IFB_INTERFACE up || { echo "Failed to set up $IFB_INTERFACE. Exiting."; exit 1; }

# Add ingress qdisc to the main interface
sudo tc qdisc add dev $INTERFACE handle ffff: ingress || {
    echo "Failed to add ingress qdisc on $INTERFACE. Exiting."; exit 1; 
}

# Redirect incoming traffic to the ifb interface
sudo tc filter add dev $INTERFACE parent ffff: protocol ip u32 match u32 0 0 action mirred egress redirect dev $IFB_INTERFACE || {
    echo "Failed to redirect traffic to $IFB_INTERFACE. Exiting."; exit 1; 
}

# Add a root qdisc to the ifb interface and limit bandwidth
sudo tc qdisc add dev $IFB_INTERFACE root tbf rate $RATE burst $BURST latency $LATENCY || {
    echo "Failed to add root qdisc on $IFB_INTERFACE. Exiting."; exit 1; 
}

echo "Traffic control setup complete. Incoming traffic on $INTERFACE is now limited to $RATE."

# Verify the configuration
echo "Current configuration:"
tc qdisc show dev $INTERFACE
tc qdisc show dev $IFB_INTERFACE
