#!/usr/bin/env python

import boto3
import sys

cf          = boto3.client('cloudformation')
autoscaling = boto3.client('autoscaling')

def disassociate_asg_with_elb(asgStackName, elbStackName):
  
  asg_resource = cf.describe_stack_resources(StackName=asgStackName, LogicalResourceId="WebASG")
  asg_name = asg_resource['StackResources'][0]['PhysicalResourceId']
  
  elb_resource = cf.describe_stack_resources(StackName=elbStackName, LogicalResourceId="WebELB")
  elb_name = elb_resource['StackResources'][0]['PhysicalResourceId']
  
  resp = autoscaling.detach_load_balancers(AutoScalingGroupName=asg_name, LoadBalancerNames=[elb_name])
  print resp

def main(argv):
  asgStackName    = sys.argv[1]
  elbStackName    = sys.argv[2]
  
  print "=== deassoc_asg_elb.py ==="
  
  disassociate_asg_with_elb(asgStackName, elbStackName)
  
if __name__ == "__main__":
    main(sys.argv[1:])
