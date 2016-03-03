#!/usr/bin/env python

import boto3
import sys
import time

cf = boto3.client('cloudformation')

def create_stack(stackName, templateFile, environmentType):
  
  # Setup parameters
  params = []
  params.append( { 'ParameterKey' : 'EnvironmentType', 'ParameterValue' : environmentType } )
  
  for asg_instance in asg_instances:
    instances.append( { 'InstanceId' : asg_instance['InstanceId'] } )
  
  # Read template file
  with open(templateFile, 'r') as file:
    template = file.read()
  
  # Create stack
  cf.create_stack(StackName=stackName,
                  TemplateBody=template,
                  Parameters=params)

def main(argv):
  stackName       = sys.argv[1]
  templateFile    = sys.argv[2]
  environmentType = sys.argv[3]
  
  create_stack(stackName, templateFile, environmentType)
  print "Started stack creation for %s", stackName
  
if __name__ == "__main__":
    main(sys.argv[1:])
