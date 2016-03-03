#!/usr/bin/env python

import boto3
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
  cf = session.resource('cloudformation')
  
else:
  cf  = boto3.client('cloudformation')

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
  stackName    = sys.argv[1]
    
  while 1:
    wait_for_stack_create(stackName)
  
if __name__ == "__main__":
    main(sys.argv[1:])
