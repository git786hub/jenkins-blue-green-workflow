#!/usr/bin/env python

import boto3
import os
import sys
import time

ASSUME_ROLE = os.environ.get('ASSUME_ROLE')

sts = boto3.client('sts')
if(ASSUME_ROLE != None):
  role = sts.assume_role(RoleArn=ASSUME_ROLE,
                         RoleSessionName="JenkinsBuild")
  session = boto3.Session(
              aws_access_key_id=role.credentials.access_key,
              aws_secret_access_key=role.credentials.secret_key,
              aws_session_token=role.credentials.session_token
            )
  cf          = session.resource('cloudformation')
  autoscaling = session.resource('autoscaling')
  
else:
  cf          = boto3.client('cloudformation')
  autoscaling = boto3.client('autoscaling')

def associate_asg_with_elb(asgStackName, elbStackName):
  
  asg_resource = cf.describe_stack_resources(StackName=asgStackName, LogicalResourceId="WebAutoScalingGroup")
  asg_name = asg_resource['StackResources'][0]['PhysicalResourceId']
  
  elb_resource = cf.describe_stack_resources(StackName=elbStackName, LogicalResourceId="ELB")
  elb_name = elb_resource['StackResources'][0]['PhysicalResourceId']
  
  resp = autoscaling.attach_load_balancers(AutoScalingGroupName=asg_name, LoadBalancerNames=[elb_name])
  print resp

def main(argv):
  asgStackName    = sys.argv[1]
  elbStackName    = sys.argv[2]
  
  associate_asg_with_elb(asgStackName, elbStackName)
  
if __name__ == "__main__":
    main(sys.argv[1:])
