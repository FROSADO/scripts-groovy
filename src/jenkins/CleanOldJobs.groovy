package jenkins

import hudson.FilePath
import hudson.model.AbstractItem;
import hudson.model.AbstractProject
import hudson.model.TopLevelItem
import jenkins.model.Jenkins

def TODAY = new Date()
def NUM_OF_DAYS = 5

// All jobs
// activeJobs = hudson.model.Hudson.instance.items
// Clean disabled job workspaces
// This is script is to be used using groovyPostBuild plugin
// you can invoke it,see 'sample_clean_projects.groovy' as example how to use it
def items = Jenkins.instance.items
def nodes = Jenkins.instance.nodes

nodes.each {node ->

}
// All workspaces shared. It's possible that a workspace is shared with several jobs.
Map<String, List<AbstractProject>> workspaces = new HashMap<>()
List<AbstractProject> disabled = new ArrayList<AbstractProject>();
List<AbstractProject> oldJobs = new ArrayList<AbstractProject>();
List<AbstractProject> jobs = new ArrayList<>()

// Extract jobs from folders
items.each { TopLevelItem item ->

    if (item.getClass().name == "com.cloudbees.hudson.plugins.folder.Folder") {
        processFolder(item, jobs);
    } else if (item instanceof AbstractProject) {
        jobs.add(item);
    } else {
        println(" Job '${item}' doesn't have workspace.");
    }
}

/**
 * Recursive function that check if the job is folder and fill the list with the non-folder
 * items.
 * <p>folderItem is a  com.cloudbees.hudson.plugins.folder.Folder class. We can't import because some servers
 * doesn't use this plugin</p>
 * @param folderItem is the Folder
 * doesn't use this plugin.
 * @param items
 * @return
 */
def processFolder (def folderItem,List<AbstractItem> items) {
    println ("Process folder " + folderItem.getName());
    for (AbstractItem f in folderItem.items){
        println (f.name + " class : " +f.getClass().getName())
        if (f.getClass().getName() ==  "com.cloudbees.hudson.plugins.folder.Folder")
        {
            processFolder (f,items)
        }
        else if (f instanceof AbstractProject )
        {
            items.add(f);
        }
        else
        {
            println (" Job '${f}' doesn't have workspace.");
        }
    }
}

// For each job, let's extact the workspace and launch the logRotator
// adding the project to disabled list if proceed

for (AbstractProject job in jobs) {


    def workspace = job.getSomeWorkspace()
    if (workspace != null) {


        List<AbstractProject> projects = workspaces.get(workspace);
        if (projects == null) {
            projects = new ArrayList<AbstractProject>();
        }

        projects.add(job)
        workspaces.put(workspace as String, projects)
        if (job.isDisabled()) {
            println(job.name + " is disabled")
            disabled.add(job)
        }
        lastBuild = job?.getLastBuild()
        if (lastBuild != null) {
            time = lastBuild.time
            println("Last build : " + (TODAY - time) + " days  :  ${job.name}")
            // More than 3 days without build. Clean workspace
            if (TODAY - time > NUM_OF_DAYS) {
                oldJobs.add(job)
                println("    Added as candidate for clean up workspace")
            }
        }
    }

    try {
        job.logRotate()
    } catch (e) {
        println("\u001B[31m Exception during logRotate " + e.getMessage()+"\u001B[0m")
    }

}

// Processing Disabled logs

println("----------------------------------------------------")
println("----------- DISABLED PROJECTS     ------------------")
println("----------------------------------------------------")

for (AbstractProject d in disabled) {
    List<AbstractProject> projects = workspaces.get(d.someWorkspace)
    println("----------------------------------------------------")
    println("----------- ${d.name}")
    println("----------------------------------------------------")
    if (projects != null && projects.size() > 1) {

        println("Workspace shared : " + d.someWorkspace + " by " + projects)
        println("     Let's validate if all projects are disabled before clean workspace")


        if (disabled.containsAll(projects)) {
            println("\u001B[32mAll disabled, let's clean the workspace\u001B[0m")
            cleanWorkspace(d)
        } else {
            println("\u001B[31m NOT ALL DISABLED. Avoid clean up \u001B[0m")
        }

    } else {
        println("Not shared workspace");
        cleanWorkspace(d)
    }

}

println("----------------------------------------------------")
println("----------- OLD  PROJECTS     ----------------------")
println("----------------------------------------------------")
for (AbstractProject jobToClean in oldJobs) {
    List<AbstractProject> projects = workspaces.get(jobToClean.someWorkspace)
    println("----------------------------------------------------")
    println("----------- ${jobToClean.name}")
    println("----------------------------------------------------")
    if (projects != null && projects.size() ) {
        println("Workspace shared : " + jobToClean.someWorkspace + " by " + projects)
        println("\u001B[32m     Let's validate if all projects are disabled or old before clean workspace\u001B[0m")
        boolean clean = true;
        for (AbstractProject project in projects) {
            if (!disabled.contains(project) && !oldJobs.contains(project) && !project.isBuilding()) {
                println("\u001B[31mWe can't clean workspace, project [${project}] is active\u001B[0m")
                clean = false;
                break;
            }
        }
        if (clean) {
            println("\u001B[32mAll disabled or also Old project, let's clean the workspace\u001B[0m")
            cleanWorkspace(jobToClean)
        } else {
            println("\u001B[31m NOT ALL DISABLED. Avoid clean up \u001B[0m")
        }
    } else {
        println("Workspace is not shared: ${jobToClean.someWorkspace}")
        cleanWorkspace(jobToClean)
        println("   ....wiped out")
    }

}

def cleanWorkspace(AbstractProject jobToClean) {
    def allNodes = Jenkins.instance.nodes;
    for (def node in allNodes) {
        println ("Deleting " + jobToClean + " in node " + node);

        FilePath workspace = node.getWorkspaceFor(jobToClean);
        if (workspace != null) {
            workspace.deleteRecursive();
        }
    }
}

println("----------------------------------------------------");
println("----------- USER CONTENTS --------------------------");
println("----------------------------------------------------");

def deleteRecursive(FilePath folder, def today, def maxDays)
{
    for (FilePath file : folder.list())
    {
        def delete = false;
        if (file.isDirectory())
        {
            deleteRecursive(file, today, maxDays);
            if (!file.getName().matches("HUB_[0-9]+") && file.list().size() == 0)
            {
                delete = true;
            }
        }
        else if ((today.getTime() - file.lastModified())/(1000*60*60*24) > maxDays)
        {
            delete = true;
        }

        if (delete)
        {
            println("Deleting " + file);
            file.delete()
        }
    }
}


deleteRecursive(Jenkins.instance.getRootPath().child("userContent"), TODAY, NUM_OF_DAYS);
