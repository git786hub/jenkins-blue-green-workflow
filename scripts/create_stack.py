#!/usr/bin/env python

import boto3
import json
import os
import sys
import time

ASSUME_ROLE = os.environ.get('ASSUME_ROLE')

sts = boto3.client('sts')
if(ASSUME_ROLE != None):
  role = sts.assume_role(RoleArn=ASSUME_ROLE,
                         RoleSessionName="JenkinsBuild")
  session = boto3.Session(
              aws_access_key_id=role['Credentials']['AccessKeyId'],
              aws_secret_access_key=role['Credentials']['SecretAccessKey'],
              aws_session_token=role['Credentials']['SessionToken']
            )
  cf = session.resource('cloudformation')
  
else:
  cf  = boto3.client('cloudformation')

def create_stack(stackName, templateFile, paramFilename, environmentType):
  
  # Setup parameters
  params = []
  params.append( { 'ParameterKey' : 'EnvironmentType', 'ParameterValue' : environmentType } )
  
  # Read paramFile
  with open(paramFilename, 'r') as paramFile:
    paramFileJson = paramFile.read()
  param_entries = json.loads(paramFileJson)
  
  # Append params from paramFile to parameter list
  if param_entries != []:
    for param_entry in param_entries:
      params.append( param_entry )
    
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
  paramFilename   = sys.argv[3]
  environmentType = sys.argv[4]
  
  create_stack(stackName, templateFile, paramFilename, environmentType)
  print "Started stack creation for %s", stackName
  
if __name__ == "__main__":
    main(sys.argv[1:])
