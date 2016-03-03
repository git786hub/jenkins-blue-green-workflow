#!/usr/bin/env python

import boto3
import json
import os
import sys
import time

cf  = boto3.client('cloudformation')

def delete_stack(stackName):
  cf.create_stack(StackName=stackName)

def main(argv):
  stackName       = sys.argv[1]
  
  delete_stack(stackName)
  print "Started stack deletion for %s" % (stackName)
  
if __name__ == "__main__":
    main(sys.argv[1:])
