function validate_user_name() {
  if (jQuery("#j_username").length == 1) {
    if (!jQuery("#j_username").val().startsWith("advantech")) {
      jQuery("#j_username").css("background-color", "red");
      jQuery("#yui-gen1-button").attr("disabled", true);
    } else {
      jQuery("#j_username").css("background-color", "white");
      jQuery("#yui-gen1-button").removeAttr("disabled");
    }
  }
}

function replaceHref() {
  jQuery('a[href^="http://devops2.advantech.ca:8080"]').each(
    function() {
      jQuery(this).attr(
        'href',
        jQuery(this).attr('href').replace(
          'http://devops2.advantech.ca:8080',
          'https://jenkins.asatnet.net'));
    });
  
  jQuery('form[action^="http://devops2.advantech.ca:8080"]').each(
    function() {
      jQuery(this).attr(
        'action',
        jQuery(this).attr('action').replace(
          'http://devops2.advantech.ca:8080',
          'https://jenkins.asatnet.net'));
    });
  
}

function replaceMRHref() {
  var href = jQuery( "a:contains('GitLab Merge Request'):not(.task-link)").attr('href')
  if (href) {
    href= href.replace(/:/,'/').replace(/git@/,'https://').replace(/\.git/,'');
    jQuery("a:contains('GitLab Merge Request')").attr('href', href);
  }
}

function harold(standIn) {
  if (standIn < 10) {
    standIn = '0' + standIn
  }
  return standIn;
}

function applySideMenu() {
  window.localStorage.setItem('sidebar-collapsed', collapsed);
  
  if (collapsed === true) {
    jQuery('div#side-panel').addClass('collapsed');
    jQuery('div#main-panel').addClass('expanded');
  } else {
    jQuery('div#side-panel').removeClass('collapsed');
    jQuery('div#main-panel').removeClass('expanded');
  }
}

function clock() {
  var time = new Date();

  var hours = time.getUTCHours();
  var minutes = time.getUTCMinutes();
  var seconds = time.getUTCSeconds();

  document.querySelectorAll('#utc-clock')[0].innerHTML = '<nobr>' + harold(hours)
    + ":" + harold(minutes) + ":" + harold(seconds) + " UTC" + '</nobr>';
}


var collapsed = window.localStorage.getItem('sidebar-collapsed')==='true';

jQuery(document)
  .ready(
    function() {
      jQuery("#j_username")
        .on("change keyup", validate_user_name);
      jQuery("form[name='login']").on("mousemove",
        validate_user_name);
      validate_user_name();
      replaceHref();
      replaceMRHref();
      var toggleButton = jQuery('<div class="task">').append(
        jQuery('<a onClick="collapsed = !collapsed;applySideMenu()" id="sidebar-collapse-link" class="task-icon-link" href="#">').append(
          jQuery('<img>')
        )
      );
      jQuery("div#tasks").append(toggleButton);
      jQuery("div.logo").append(jQuery('<div id="utc-clock">'));


      applySideMenu();

      setInterval(clock, 1000);
      jQuery("div.cbwf-stage-view")
        .detach()
        .appendTo('div#main-panel');
    });