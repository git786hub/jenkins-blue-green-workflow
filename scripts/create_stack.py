#!/usr/bin/env python

from boto3.session import Session

import boto3
import sys
import time

cf = boto3.client('cloudformation')

def create_stack(stackName, templateFile):
  with open(templateFile, 'r') as file:
    template = file.read()
  cf.create_stack(StackName=stackName, TemplateBody=template)

def wait_for_stack_create(stackName):
  stack_description = cf.describe_stacks(StackName=stackName)
  status = stack_description['Stacks'][0]['StackStatus']
  
  print "%s: %s", stackName, status
  
  if status in ['UPDATE_COMPLETE', 'CREATE_COMPLETE']:
    print "Stack %s has been created", stackName
    sys.exit(0)
        
  elif status in ['UPDATE_IN_PROGRESS', 'UPDATE_ROLLBACK_IN_PROGRESS', 
      'UPDATE_ROLLBACK_COMPLETE_CLEANUP_IN_PROGRESS', 'CREATE_IN_PROGRESS', 
      'ROLLBACK_IN_PROGRESS']:
    time.sleep(15)
       
  else:
    print "Stack %s failed to create!", stackName
    sys.exit(1)

def main(argv):
  stackName    = sys.argv[2]
  templateFile = sys.argv[3]
  
  create_stack(stackName, templateFile)
  
  while 1:
    wait_for_stack_create(stackName)
  
if __name__ == "__main__":
    main(sys.argv[1:])
