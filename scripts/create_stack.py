#!/usr/bin/env python

import boto3
import json
import sys
import time

cf = boto3.client('cloudformation')

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
