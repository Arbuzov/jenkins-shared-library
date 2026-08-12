import org.apache.commons.io.FilenameUtils;

def call(String baseUrl, fileList = []) {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)
}