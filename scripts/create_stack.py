#!/usr/bin/env python

import boto3
import sys
import time

cf = boto3.client('cloudformation')

def create_stack(awsRegion, stackName, templateFile):
  with open(templateFile, 'r') as file:
    template = file.read()
  cf.create_stack(StackName=stackName, TemplateBody=template)

def wait_for_stack_create(awsRegion, stackName):
  stack_description = cf.describe_stacks(StackName=stackName)
  status = stack_description['Stacks'][0]['StackStatus']
  
  print "${stackName}: ${status}"
  
  if status in ['UPDATE_COMPLETE', 'CREATE_COMPLETE']:
    print "Stack ${stackName} has been created"
    sys.exit(0)
        
  elif status in ['UPDATE_IN_PROGRESS', 'UPDATE_ROLLBACK_IN_PROGRESS', 
      'UPDATE_ROLLBACK_COMPLETE_CLEANUP_IN_PROGRESS', 'CREATE_IN_PROGRESS', 
      'ROLLBACK_IN_PROGRESS']:
    time.sleep(15)
       
  else:
    print "Stack ${stackName} failed to create!"
    sys.exit(1)

def main(argv):
  awsRegion    = sys.argv[1]
  stackName    = sys.argv[2]
  templateFile = sys.argv[3]
  
  create_stack(awsRegion, stackName, templateFile)
  
  while 1:
    wait_for_stack_create(awsRegion, stackName)
  
if __name__ == "__main__":
    main(sys.argv[1:])