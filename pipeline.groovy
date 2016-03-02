@Grab('com.amazonaws:aws-java-sdk-cloudformation:1.10.57')

import java.util.List

node {
   // Mark the code checkout 'stage'....
   stage 'Checkout'
   sayHello()

   // Get some code from a GitHub repository
   git url: 'https://github.com/vinayselvaraj/vpc2vpc.git'

   // Get the maven tool.
   // ** NOTE: This 'M3' maven tool must be configured
   // **       in the global configuration.           
   def mvnHome = tool 'M3'

   // Mark the code build 'stage'....
   stage 'Build'
   // Run the maven build
   sh "${mvnHome}/bin/mvn clean install"
}

def sayHello() {
  print 'Hello!'
}