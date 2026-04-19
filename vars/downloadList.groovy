import org.apache.commons.io.FilenameUtils;

def call(String baseUrl, fileList = []) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
}