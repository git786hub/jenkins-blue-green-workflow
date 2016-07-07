#!/usr/bin/env groovy

@Grab(group='com.amazonaws', module='aws-java-sdk-cloudformation', version='1.10.57')
@Grab(group='com.amazonaws', module='aws-java-sdk-autoscaling', version='1.10.57')

import com.amazonaws.regions.Regions
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient
import com.amazonaws.services.cloudformation.model.ListStacksRequest
import com.amazonaws.services.cloudformation.model.StackSummary
import com.amazonaws.services.cloudformation.model.Stack
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest
import com.amazonaws.services.cloudformation.model.DescribeStackResourcesRequest
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest
import com.amazonaws.services.autoscaling.model.ResumeProcessesRequest
import com.amazonaws.services.autoscaling.model.SuspendProcessesRequest

import java.util.List

class BGDeploy {
  def region
  def amiId
  def elbStackName

  def WEB_ASG_STACK_NAME
  def STAGING_ELB_STACK_NAME
  def PREVIOUS_ASG_STACK_NAME
  def PRODUCTION_ELB_STACK_NAME

  def props = new Properties()
  def env = System.getenv()
  def prop_file = new File("bgdeploy.properties")
  
  def CFN_CLIENT = new AmazonCloudFormationClient()
  def ASG_CLIENT = new AmazonAutoScalingClient()

  def init() {
  
    props = new Properties()
    setRegion()
  
    def WEB_ASG_STACK_NAME_PREFIX = env['JOB_NAME'] + "-web-asg-"
    WEB_ASG_STACK_NAME =  WEB_ASG_STACK_NAME_PREFIX + env['BUILD_NUMBER']
    STAGING_ELB_STACK_NAME = env['JOB_NAME'] + "-test-elb-" + env['BUILD_NUMBER']
  
    props.put("WEB_ASG_STACK_NAME", WEB_ASG_STACK_NAME)
    props.put("STAGING_ELB_STACK_NAME", STAGING_ELB_STACK_NAME)
    
    //def proc = "scripts/get_latest_stack.groovy ${WEB_ASG_STACK_NAME_PREFIX} bluegreen-poc-elb-prod > LATEST_STACK_NAME".execute()
    PREVIOUS_ASG_STACK_NAME = getLatestStack(WEB_ASG_STACK_NAME_PREFIX, elbStackName)
    
    if(PREVIOUS_ASG_STACK_NAME != null) {
      PREVIOUS_ASG_STACK_NAME = PREVIOUS_ASG_STACK_NAME.trim()
      
      println "Previous stack name: ${PREVIOUS_ASG_STACK_NAME}"
      props.put("PREVIOUS_ASG_STACK_NAME", PREVIOUS_ASG_STACK_NAME)
    }

    PRODUCTION_ELB_STACK_NAME = elbStackName
    println "Production ELB stack name: ${PRODUCTION_ELB_STACK_NAME}"
    
    props.put("PRODUCTION_ELB_STACK_NAME", PRODUCTION_ELB_STACK_NAME)
  
    props.put("region", region)
    props.put("amiId", amiId)
  
    // Process property files
    process_property_files()
  
    // Write properties for later use
    props.store(prop_file.newWriter(), null)
  }
  
  def setRegion() {
      // Set regions
      def AWS_DEFAULT_REGION = Regions.fromName(region)
      CFN_CLIENT.region = AWS_DEFAULT_REGION
      ASG_CLIENT.region = AWS_DEFAULT_REGION
  }

  def load_props() {
    props.load(prop_file.newDataInputStream())
    WEB_ASG_STACK_NAME = props.get("WEB_ASG_STACK_NAME")
    STAGING_ELB_STACK_NAME = props.get("STAGING_ELB_STACK_NAME")
    PREVIOUS_ASG_STACK_NAME = props.get("PREVIOUS_ASG_STACK_NAME")
    PRODUCTION_ELB_STACK_NAME = props.get("PRODUCTION_ELB_STACK_NAME")
  
    region = props.get("region")
    amiId = props.get("amiId")
    
    setRegion()
  }

  def process_property_files() {
    println "Updating AMI ID ${amiId} in properties file"
    
    def web_asg_properties = new File("./params/${region}-web_asg.properties")
    def web_asg_properties_text = web_asg_properties.text
    web_asg_properties_text = web_asg_properties_text.replaceAll("__AMIID__", amiId)
    web_asg_properties.write(web_asg_properties_text)
    
  }

  def create_stacks() {
    createCfnStack(region, "cfn/web_asg.json", "params/${region}-web_asg.properties", WEB_ASG_STACK_NAME, "production")
    createCfnStack(region, "cfn/web_elb.json", "params/${region}-web_elb.properties", STAGING_ELB_STACK_NAME, "test")
 
    // Wait for CloudFormation stack creation
    println "Waiting for stacks to be created"
    waitForCfnStackCreation(region, WEB_ASG_STACK_NAME)
    waitForCfnStackCreation(region, STAGING_ELB_STACK_NAME)
    
    // Update autoscaling group in web stack to match the min/max/desired counts in previous prod stack
    def previous_asg_counts = null
    if(PREVIOUS_ASG_STACK_NAME != null) {
      previous_asg_counts = getWebASGMinMaxDesiredCounts(PREVIOUS_ASG_STACK_NAME)
      println "DEBUG: previous_asg_counts=${previous_asg_counts}"
      updateASGCounts(WEB_ASG_STACK_NAME, previous_asg_counts)
    }
    
    // Pause Scaling
    suspendScaling(WEB_ASG_STACK_NAME);
    
    // Wait for autoscaling group to scale to desired count
    if(previous_asg_counts != null) {
      println "Waiting for autoscaling group in stack ${WEB_ASG_STACK_NAME} to scale to desired count"
      waitForASGToScaleToDesirecCount(WEB_ASG_STACK_NAME, previous_asg_counts.get("desired"))
    }
  
    // Associate Staging ELB with AutoScaling group
    println "Associating ASG ${WEB_ASG_STACK_NAME} with ELB ${STAGING_ELB_STACK_NAME}"
    associateASGWithELB(region, WEB_ASG_STACK_NAME,  STAGING_ELB_STACK_NAME)
  
    // Wait for the ELB to put instances in service
    println "Waiting for ASG instances to go in service"
    waitForASGInstancesToGoInService(region, WEB_ASG_STACK_NAME, STAGING_ELB_STACK_NAME)
 
  }

  def flip_green_to_blue() {
    
    // Disassociate Staging ELB from Green ASG
    println "Disassocating Staging ELB ${STAGING_ELB_STACK_NAME} from Green ASG ${WEB_ASG_STACK_NAME}"
    disassociateASGWithELB(region, WEB_ASG_STACK_NAME,  STAGING_ELB_STACK_NAME)

    // Associate Production ELB with AutoScaling group
    println "Associating production ELB ${PRODUCTION_ELB_STACK_NAME} with Green ASG ${WEB_ASG_STACK_NAME}"
    associateASGWithELB(region, WEB_ASG_STACK_NAME,  PRODUCTION_ELB_STACK_NAME)
    
    // Update autoscaling group in web stack to match the min/max/desired counts in previous prod stack
    def previous_asg_counts = null
    if(PREVIOUS_ASG_STACK_NAME != null) {
      previous_asg_counts = getWebASGMinMaxDesiredCounts(PREVIOUS_ASG_STACK_NAME)
      println "DEBUG: previous_asg_counts=${previous_asg_counts}"
      updateASGCounts(WEB_ASG_STACK_NAME, previous_asg_counts)
    }
    
    // Wait for autoscaling group to scale to desired count
    if(previous_asg_counts != null) {
      println "Waiting for autoscaling group in stack ${WEB_ASG_STACK_NAME} to scale to desired count"
      waitForASGToScaleToDesirecCount(WEB_ASG_STACK_NAME, previous_asg_counts.get("desired"))
    }
 
    // Wait for the ELB to put instances in service
    println "Waiting for instances to go in service"
    waitForASGInstancesToGoInService(region, WEB_ASG_STACK_NAME, PRODUCTION_ELB_STACK_NAME)
    
    // Disassociate Blue ASG from prod ELB
    if(PREVIOUS_ASG_STACK_NAME != null) {
      println "Disassociating blue ASG stack ${PREVIOUS_ASG_STACK_NAME} from prod ELB ${PRODUCTION_ELB_STACK_NAME}"
      disassociateASGWithELB(region, PREVIOUS_ASG_STACK_NAME,  PRODUCTION_ELB_STACK_NAME)
    }
    
    // Resume scaling
    resumeScaling(WEB_ASG_STACK_NAME)
  }

  def rollback_deployment() {
    if(PREVIOUS_ASG_STACK_NAME == null || PREVIOUS_ASG_STACK_NAME.isEmpty() ) {
      println "There is no previous deployment so there is nothing to rollback to.  Cleaning up test ELB stack"      
      deleteCfnStack(region, STAGING_ELB_STACK_NAME)
    
      } else {
        // Associate Prod ELB with previous build's ASG
        println "Rolling back deployment"
        
        println "Associating ASG ${PREVIOUS_ASG_STACK_NAME} with ELB ${PRODUCTION_ELB_STACK_NAME}"
        associateASGWithELB(region, PREVIOUS_ASG_STACK_NAME,  PRODUCTION_ELB_STACK_NAME)
        
        // Update previous prod autoscaling group in web stack to match the min/max/desired counts in green stack
        def previous_asg_counts = null
        if(PREVIOUS_ASG_STACK_NAME != null) {
          previous_asg_counts = getWebASGMinMaxDesiredCounts(WEB_ASG_STACK_NAME)
          println "DEBUG: previous_asg_counts=${previous_asg_counts}"
          updateASGCounts(PREVIOUS_ASG_STACK_NAME, previous_asg_counts)
        }
        
        // Pause scaling
        suspendScaling(PREVIOUS_ASG_STACK_NAME);
    
        // Wait for autoscaling group to scale to desired count
        if(previous_asg_counts != null) {
          println "Waiting for autoscaling group in stack ${PREVIOUS_ASG_STACK_NAME} to scale to desired count"
          waitForASGToScaleToDesirecCount(PREVIOUS_ASG_STACK_NAME, previous_asg_counts.get("desired"))
        }

        // Wait for the ELB to put instances in service
        println "Waiting for ASG instances in ${PREVIOUS_ASG_STACK_NAME} to go in service "
        waitForASGInstancesToGoInService(region, PREVIOUS_ASG_STACK_NAME, PRODUCTION_ELB_STACK_NAME)
  
        // Disassociate Prod ELB from green ASG
        println "Disassociating ASG ${WEB_ASG_STACK_NAME} from production ELB ${PRODUCTION_ELB_STACK_NAME}"
        disassociateASGWithELB(region, WEB_ASG_STACK_NAME,  PRODUCTION_ELB_STACK_NAME)
  
        // Associate Staging ELB with AutoScaling group
        println "Associating ASG ${WEB_ASG_STACK_NAME} with ELB ${STAGING_ELB_STACK_NAME}"
        associateASGWithELB(region, WEB_ASG_STACK_NAME,  STAGING_ELB_STACK_NAME)

        // Wait for the ELB to put instances in service
        println "Waiting for instances to go in service"
        waitForASGInstancesToGoInService(region, WEB_ASG_STACK_NAME, STAGING_ELB_STACK_NAME)
        
        // Resume scaling
        resumeScaling(PREVIOUS_ASG_STACK_NAME);
      }
    }

    def cleanup() {
      println "Cleaning up"
      
      // Decom old stacks
      println "Deleting stack ${STAGING_ELB_STACK_NAME}"
      deleteCfnStack(region, STAGING_ELB_STACK_NAME)

      if(PREVIOUS_ASG_STACK_NAME != null && PREVIOUS_ASG_STACK_NAME.length() > 0) { // Skip of nothing to cleanup
        
        // Decom old stacks
        println "Deleting stack ${PREVIOUS_ASG_STACK_NAME}"
        deleteCfnStack(region, PREVIOUS_ASG_STACK_NAME)
      }
    }

    def createCfnStack(def awsRegion, def templateFile, def paramFile, def stackName, def environmentType) {
      // Execute create-stack command
      def cmd = "scripts/create_stack.py ${stackName} ${templateFile} ${paramFile} ${environmentType}"
      execute_command(cmd, ["AWS_DEFAULT_REGION=${awsRegion}"])
    }

    def deleteCfnStack(def awsRegion, def stackName) {
      // Execute create-stack command
      def cmd = "scripts/delete_stack.py ${stackName}"
      execute_command(cmd, ["AWS_DEFAULT_REGION=${awsRegion}"])
    }

    def waitForCfnStackCreation(def awsRegion, def stackName) {
      def cmd = "scripts/wait_for_stack_create.py ${stackName} "
      execute_command(cmd, ["AWS_DEFAULT_REGION=${awsRegion}"])
    }

    def associateASGWithELB(def awsRegion, def asgStack, def elbStack) {
      def cmd = "scripts/assoc_asg_elb.py ${asgStack} ${elbStack} "
      execute_command(cmd, ["AWS_DEFAULT_REGION=${awsRegion}"])
    }

    def disassociateASGWithELB(def awsRegion, def asgStack, def elbStack) {
      def cmd = "scripts/deassoc_asg_elb.py ${asgStack} ${elbStack} "
      execute_command(cmd, ["AWS_DEFAULT_REGION=${awsRegion}"])
    }

    def waitForASGInstancesToGoInService(def awsRegion, def asgStack, def elbStack) {
      def cmd = "scripts/wait_for_asg_elb_registration.py ${asgStack} ${elbStack} "
      execute_command(cmd, ["AWS_DEFAULT_REGION=${awsRegion}"])
    }
    
    def execute_command(def cmd, def envVars) {
      def proc = cmd.execute(envVars, null)
      //proc.waitForProcessOutput(System.out, System.err)
      proc.consumeProcessOutput(System.out, System.err)
      proc.waitFor()
      if(proc.exitValue() != 0) {
        throw new RuntimeException("Command execution failed")
      }
    }
    
    def getLatestStack(def webStackPrefix, def elbStackName) {
      
      println "DEBUG: webStackPrefix=${webStackPrefix} elbStackName=${elbStackName}\n"
  
      def stackSummaries = new ArrayList<StackSummary>()
 
      def listStacksRequest = new ListStacksRequest()
      def stackStatusFilters = new ArrayList()
      stackStatusFilters.add("CREATE_COMPLETE")
      listStacksRequest.stackStatusFilters = stackStatusFilters
  
      // Get all stacks
      while(true) {
        def listStacksResult = CFN_CLIENT.listStacks(listStacksRequest)
        stackSummaries.addAll(listStacksResult.stackSummaries)
        if(listStacksResult.nextToken != null) {
          listStacksRequest.nextToken = listStacksResult.nextToken
        } else {
          break
        }
      }
      println "DEBUG: stackSummaries=${stackSummaries}\n"
  
      def prefixMatchingStacks = new ArrayList<StackSummary>()
  
      // Get all stacks that match the prefix
      for(int i=0; i<stackSummaries.size(); i++) { //
        def stackSummary = stackSummaries.get(i)
        if(stackSummary.stackName.startsWith(webStackPrefix)) {
          prefixMatchingStacks.add(stackSummary)
        }
      }
      
      println "DEBUG: prefixMatchingStacks=${prefixMatchingStacks}\n"
  
      // Get ELB name from ELB stack
      def elbName = null
      def elbStackElbResource = getCloudFormationResource(elbStackName, "WebELB")
      if(elbStackElbResource != null) {
        elbName = elbStackElbResource.physicalResourceId
      }
  
      def stacksAssociatedWithELB = new ArrayList()
    
      // Find ASGs associated with the ELB in the ELB stack
      for(int i=0; i<prefixMatchingStacks.size(); i++) {
        def stackSummary = prefixMatchingStacks.get(i)
        def stackName = stackSummary.stackName
    
        def webStackAsgName = null
        def webStackAsgResource = getCloudFormationResource(stackName, "WebASG")
        if(webStackAsgResource != null) {
          webStackAsgName = webStackAsgResource.physicalResourceId
        }
    
        def descAsgResult = ASG_CLIENT.describeAutoScalingGroups(
          new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(webStackAsgName))
        if(descAsgResult != null && !descAsgResult.autoScalingGroups.isEmpty() ) {
          def asg = descAsgResult.autoScalingGroups.get(0)
      
          asg.loadBalancerNames.each { asgElbName ->
            if(asgElbName.equals(elbName)) {
              stacksAssociatedWithELB.add(stackName)
            }
          }
      
        }  
      }
      
      println "DEBUG: stacksAssociatedWithELB=${stacksAssociatedWithELB}\n"
  
      if(stacksAssociatedWithELB.size() > 1) {
        throw new RuntimeException("More than one stack found that is associated with the ELB")
      } else if(stacksAssociatedWithELB.size() == 0) {
        return null
      }
  
      return stacksAssociatedWithELB.get(0)  
    }
    
    def suspendScaling(def asgStackName) {
        
        def webStackAsgName = null
        def webStackAsgResource = getCloudFormationResource(asgStackName, "WebASG")
        if(webStackAsgResource != null) {
          webStackAsgName = webStackAsgResource.physicalResourceId
        }
        
        ASG_CLIENT.suspendProcesses(new SuspendProcessesRequest()
                                        .withScalingProcesses(["AlarmNotification"])
                                        .withAutoScalingGroupName(webStackAsgName)
                                    )
    }
    
    def resumeScaling(def asgStackName) {
        
        def webStackAsgName = null
        def webStackAsgResource = getCloudFormationResource(asgStackName, "WebASG")
        if(webStackAsgResource != null) {
          webStackAsgName = webStackAsgResource.physicalResourceId
        }
        
        ASG_CLIENT.resumeProcesses(new ResumeProcessesRequest()
                                        .withScalingProcesses(["AlarmNotification"])
                                        .withAutoScalingGroupName(webStackAsgName)
                                    )
    }

    def getCloudFormationStackByName(def stackName) {
      def descStacksResult = CFN_CLIENT.describeStacks(new DescribeStacksRequest().withStackName(stackName))
  
      if(descStacksResult == null || descStacksResult.stacks == null) {
        return null
      }
  
      return descStacksResult.stacks.get(0)
    }

    def getCloudFormationResource(def stackName, def logicalResourceName) {
      def descStackResourcesRequest = new DescribeStackResourcesRequest()
      descStackResourcesRequest.stackName = stackName
      descStackResourcesRequest.logicalResourceId = logicalResourceName
  
      def descStackResourcesResult = CFN_CLIENT.describeStackResources(descStackResourcesRequest)
      if(descStackResourcesResult != null) {
        def stackResources = descStackResourcesResult.stackResources
        if(stackResources != null && !stackResources.isEmpty()) {
          return stackResources.get(0)
        }
      }
      return null
    }
    
    def getWebASGMinMaxDesiredCounts(def stackName) {
  
      def webStackAsgName = null
      def webStackAsgResource = getCloudFormationResource(stackName, "WebASG")
      if(webStackAsgResource != null) {
        webStackAsgName = webStackAsgResource.physicalResourceId
      }
  
      def descAsgRequest = new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(webStackAsgName)
      def groups = ASG_CLIENT.describeAutoScalingGroups(descAsgRequest).getAutoScalingGroups()
      println webStackAsgName
  
      def counts = null
  
      if(!groups.isEmpty()) {
        def group = groups.get(0)
        println group
        counts = new HashMap()
        counts.put("min", group.minSize)
        counts.put("max", group.maxSize)
        counts.put("desired", group.desiredCapacity)
      }
  
      return counts
    }
    
    def updateASGCounts(def stackName, def counts) {
  
      def webStackAsgName = null
      def webStackAsgResource = getCloudFormationResource(stackName, "WebASG")
      if(webStackAsgResource != null) {
        webStackAsgName = webStackAsgResource.physicalResourceId
      }
  
      def updateASGRequest = new UpdateAutoScalingGroupRequest()
      updateASGRequest.minSize = counts.get("min")
      updateASGRequest.maxSize = counts.get("max")
      updateASGRequest.desiredCapacity = counts.get("desired")
      updateASGRequest.autoScalingGroupName = webStackAsgName
      def updateASGResult = ASG_CLIENT.updateAutoScalingGroup(updateASGRequest)
      println "Updating AutoScaling Group in stack ${stackName} to counts ${counts}. Result=${updateASGResult}"

    }
    
    def waitForASGToScaleToDesirecCount(def stackName, def desiredCount) {
      def TIMEOUT = 900
  
      def time_start = System.currentTimeMillis()
      def done = false
  
      while(!done) {
        
        def webStackAsgName = null
        def webStackAsgResource = getCloudFormationResource(stackName, "WebASG")
        if(webStackAsgResource != null) {
          webStackAsgName = webStackAsgResource.physicalResourceId
        }
        
        def descAsgRequest = new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(webStackAsgName)
        def groups = ASG_CLIENT.describeAutoScalingGroups(descAsgRequest).getAutoScalingGroups()
        
        def instances = null
    
        if(!groups.isEmpty()) {
          def group = groups.get(0)
          instances = group.instances
        }
        
        if(instances.size() == desiredCount) {
          println "ASG in stack ${stackName} has scaled to the desired count: ${desiredCount}"
          done = true
        } else {
          println "Waiting for ASG in stack ${stackName} to scale up to desired count (${desiredCount}), currently at ${instances.size()}"
          Thread.sleep(60 * 1000)
      
          def seconds_elapsed = (System.currentTimeMillis() - time_start) / 1000
          if(seconds_elapsed > TIMEOUT) {
            println "ERROR: Timed out waiting for ASG in stack ${stackName} to scale to desired count"
            throw new RuntimeException("Timed out waiting for ASG in stack ${stackName} to scale to desired count")
          }
        }
      }
    }
    
}

// Main
def cli = new CliBuilder(usage: 'BGDeploy')
cli.with {
  h longOpt: 'help', 'Prints this help'
  i longOpt: 'init', 'Initializes deployment'
  x longOpt: 'create-stacks', 'Creates Blue & Test ELB stacks'
  f longOpt: 'flip-green-to-blue', 'Flips Green to Blue'
  b longOpt: 'rollback', 'Rolls back deployment'
  c longOpt: 'cleanup', 'Cleans up deployment'
  p longOpt: 'process-properties', 'Updates property files with AMI ID'
  
  r longOpt: 'region', 'AWS Region', args: 1, required: false
  a longOpt: 'ami-id', 'AMI ID to use', args: 1, required: false
  s longOpt: 'elb-stack', 'Prod ELB Stack Name', args: 1, required: false
}



def opt = cli.parse(args)
if (!opt) return
if (opt.h) cli.usage()

def bg = new BGDeploy()

bg.region = opt.r
bg.amiId = opt.a
bg.elbStackName = opt.s

if(opt.i) {
  bg.init()
  return
}

bg.load_props()

if(opt.p) {
  bg.process_property_files()
}

if(opt.x) {
  bg.create_stacks()
}

if(opt.f) {
  bg.flip_green_to_blue()
}

if(opt.b) {
  bg.rollback_deployment()
}

if(opt.c) {
  bg.cleanup()
}