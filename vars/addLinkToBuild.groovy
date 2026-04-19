def call(Map arg) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  String link = arg.LINK ?: ''
  String text = arg.TEXT ?: link
  Boolean blank = arg.BLANK ?: true
  String header = arg.HEADER ?: ''
  String icon = arg.ICON ?: 'folder.gif'
  def summary=manager.createSummary(icon);
  if (!header.equals('')) {
    summary.appendText("<h2>${header}</h2>", false)
  }
  summary.appendText("<a href='${link}' ${blank ? 'target="_blank"' : ''}>${text}</a>", false)
}
