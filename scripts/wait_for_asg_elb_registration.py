#!/usr/bin/env python

import boto3
import os
import sys
import time

cf          = boto3.client('cloudformation')
autoscaling = boto3.client('autoscaling')
elb         = boto3.client('elb')

TIMEOUT = 900 # 15 minutes

def wait_for_asg_elb_registration(asgStackName, elbStackName):
  
  asg_resource = cf.describe_stack_resources(StackName=asgStackName, LogicalResourceId="WebASG")
  asg_name = asg_resource['StackResources'][0]['PhysicalResourceId']
  
  elb_resource = cf.describe_stack_resources(StackName=elbStackName, LogicalResourceId="WebELB")
  elb_name = elb_resource['StackResources'][0]['PhysicalResourceId']

  asg_group = autoscaling.describe_auto_scaling_groups(AutoScalingGroupNames=[asg_name])['AutoScalingGroups'][0]

  asg_instances = asg_group['Instances']
  instances = []

  for asg_instance in asg_instances:
    instances.append( { 'InstanceId' : asg_instance['InstanceId'] } )
  
  start_time = int(time.time())
  
  while 1:
    try:
      
      
      
      elb_desc_health_resp = elb.describe_instance_health(LoadBalancerName=elb_name, Instances=instances)
      instance_states = elb_desc_health_resp['InstanceStates']
    
      all_in_service = True
      for instance_state in instance_states:
        if instance_state['State'] != 'InService':
          all_in_service = False
          break
    
      if all_in_service:
        print "All instances are now in service. ASG=%s ELB=%s" % (asg_name, elb_name)
        sys.exit(0)
    
      current_time = int(time.time())
      elapsed_time = current_time - start_time
      if(elapsed_time > TIMEOUT):
        print "Timed out waiting for instances to go in service."
        sys.exit(1)
    
    except Exception as ex:
      print "Unable to describe instances in the ELB at this time.  Instances may not have been registered with the ELB yet."
      print ex
    
    print "Waiting for instances to go in service."
    time.sleep(60)
    

def main(argv):
  asgStackName    = sys.argv[1]
  elbStackName    = sys.argv[2]
  
  wait_for_asg_elb_registration(asgStackName, elbStackName)
  
if __name__ == "__main__":
    main(sys.argv[1:])
