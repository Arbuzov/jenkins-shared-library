def call(Map args) {
    reportUsage(getClass().protectionDomain.codeSource.location.path)
    def mask = args.mask
    def filesContent = ''
    def fileList = findFiles(glob: mask)
    fileList.each { file ->
        def content = readFile(file.path)
        content = content.replaceAll(/(\r\n|\r|\n)/, '\\\\n')
        filesContent += file.name + '\\n' +content + '\\n'
    }
    return filesContent
}