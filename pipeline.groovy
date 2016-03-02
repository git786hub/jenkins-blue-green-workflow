
import static groovy.io.FileType.FILES

node {
   // Mark the code checkout 'stage'....
   stage 'Checkout'
   sayHello()

   // Get some code from a GitHub repository
   git url: 'https://github.com/vinayselvaraj/jenkins-blue-green-workflow.git'

   // Mark the code build 'stage'....
   stage 'DeployStaging'
   
   def mvnHome = tool 'M3'
   sh "${mvnHome}/bin/mvn -B -Dmaven.test.failure.ignore -f deployer/pom.xml clean package" 
   
   // Run the maven build
   sayHello()
}

def sayHello() {
  print 'Hello!'
}