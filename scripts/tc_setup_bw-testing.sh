#!/bin/bash

INTERFACE="lo"

sudo tc qdisc del dev $INTERFACE root 2>/dev/null
sudo tc qdisc add dev $INTERFACE root handle 1: htb default 30

sudo tc class add dev $INTERFACE parent 1: classid 1:1 htb rate 1gbit ceil 1gbit
sudo tc class add dev $INTERFACE parent 1:1 classid 1:10 htb rate 588mbit ceil 1gbit
sudo tc class add dev $INTERFACE parent 1:1 classid 1:20 htb rate 412mbit ceil 700mbit

sudo tc filter add dev $INTERFACE protocol ip parent 1: prio 1 u32 match ip dport 10005 0xffff flowid 1:10
sudo tc filter add dev $INTERFACE protocol ip parent 1: prio 1 u32 match ip dport 20005 0xffff flowid 1:20

sudo tc -s qdisc show dev $INTERFACE
sudo tc -s class show dev $INTERFACE

echo "HTB configuration with capped bandwidth applied to $INTERFACE"
