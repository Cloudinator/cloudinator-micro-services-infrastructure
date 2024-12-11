def call(List dependencies, String folderPath = "") {
    if (dependencies.isEmpty()) {
        echo "No dependencies to check."
        return
    }

    echo "Checking dependencies: ${dependencies}"

    dependencies.each { dependency ->
        // Construct the full path to the job
        def depJobPath = folderPath ? "${folderPath}/${dependency}-pipeline" : "${dependency}-pipeline"
        echo "Looking for job: ${depJobPath}"

        // Retrieve the job using the full path
        def depJob = Jenkins.instance.getItemByFullName(depJobPath)
        if (depJob == null) {
            error "Dependency job '${depJobPath}' not found. Ensure the job exists and the name is correct."
        }

        def lastSuccessfulBuild = depJob.getLastSuccessfulBuild()
        if (lastSuccessfulBuild == null) {
            error "Dependency '${depJobPath}' has not been successfully built yet."
        }

        echo "Dependency '${depJobPath}' is ready (Last successful build: ${lastSuccessfulBuild.getDisplayName()})"
    }

    echo "All dependencies are ready"
}
