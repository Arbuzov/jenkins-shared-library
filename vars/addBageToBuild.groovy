import com.jenkinsci.plugins.badge.action.BadgeAction

@NonCPS
def call(build, bage, url = 'none') {
   def action = BadgeAction.createShortText(bage, 'white', 'green', 'white', 'white', url)
   build.addAction(action)
}