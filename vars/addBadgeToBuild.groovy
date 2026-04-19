import com.jenkinsci.plugins.badge.action.BadgeAction

@NonCPS
def call(build, badge, url = 'none') {
  def action = BadgeAction.createShortText(badge, 'white', 'green', 'white', 'white', url)
  build.addAction(action)
}

def call(Object args) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  return this.call(args.build, args.badge, args.url ?: 'none')
}