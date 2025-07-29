package com.spacebridge

class NetboxHelper {
  static List<String> getNetboxTenants(def context, Map arg = [:]) {
    return context.getNetboxTenants(arg)
  }
  
  static List<String> getNetboxRacks(def context, Map arg = [:]) {
    return context.getNetboxRacks(arg)
  }
  
  static List<String> getNetboxDevices(def context, Map arg = [:]) {
    return context.getNetboxDevices(arg)
  }
}
