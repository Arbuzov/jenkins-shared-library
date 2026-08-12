// loadSharedLibraryFile(): Load a file from shared library to a destination filename

def call(String srcFileName, String dstFileName = '', String dstFileMode = '0644') {
	reportUsage(path: getClass().protectionDomain.codeSource.location.path)
	String dst2 = dstFileName
	if (dst2.equals('')) {
		dst2 = srcFileName
	}

	def fileContents = libraryResource "com/spacebridge/pipeline/pipelinelib/${srcFileName}"
	writeFile file: "${dst2}", text: fileContents
	sh "chmod \"${dstFileMode}\" \"${dst2}\""
}
