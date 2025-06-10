def call(String summaryText, String header = '', String icon = 'folder.gif') {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  def summary=manager.createSummary(icon);
  if (!header.equals('')) {
    summary.appendText("<h2>${header}</h2>", false)
  }
  summary.appendText(summaryText, false)
}

def call(Map params) {
  def summaryText = params.summaryText ?: ''
  def header = params.header ?: ''
  def icon = params.icon ?: 'folder.gif'
  return this.call(summaryText, header, icon)
}
