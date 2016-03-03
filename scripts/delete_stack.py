#!/usr/bin/env python

import boto3
import sys

cf  = boto3.client('cloudformation')

def delete_stack(stackName):
  print "Starting stack deletion for %s" % (stackName)
  result = cf.delete_stack(StackName=stackName)
  print result

def main(argv):
  stackName       = sys.argv[1]
  print "=== delete_stack.py ==="
  
  delete_stack(stackName)
  print "Started stack deletion for %s" % (stackName)
  
if __name__ == "__main__":
    main(sys.argv[1:])
