def call(List dependencies) {
    if (dependencies.isEmpty()) {
        echo "No dependencies to check."
        return
    }

    echo "Checking dependencies: ${dependencies}"
    
    dependencies.each { dependency ->
        def depJob = Jenkins.instance.getItemByFullName("${dependency}-pipeline")
        if (depJob == null) {
            error "Dependency job ${dependency}-pipeline not found"
        }
        
        def lastSuccessfulBuild = depJob.getLastSuccessfulBuild()
        if (lastSuccessfulBuild == null) {
            error "Dependency ${dependency} has not been successfully built yet"
        }
        
        echo "Dependency ${dependency} is ready (Last successful build: ${lastSuccessfulBuild.getDisplayName()})"
    }
    
    echo "All dependencies are ready"
}

