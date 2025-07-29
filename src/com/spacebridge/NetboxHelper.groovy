package com.spacebridge

class NetboxHelper {
  static List<String> getNetboxTenants(def context, Map arg = [:]) {
    if (!context.respondsTo('getNetboxTenants')) {
      throw new IllegalArgumentException('Context does not support getNetboxTenants method')
    }
    return context.getNetboxTenants(arg)
  }
  
  static List<String> getNetboxRacks(def context, Map arg = [:]) {
    if (!context.respondsTo('getNetboxRacks')) {
      throw new IllegalArgumentException('Context does not support getNetboxRacks method')
    }
    return context.getNetboxRacks(arg)
  }
  
  static List<String> getNetboxDevices(def context, Map arg = [:]) {
    if (!context.respondsTo('getNetboxDevices')) {
      throw new IllegalArgumentException('Context does not support getNetboxDevices method')
    }
    return context.getNetboxDevices(arg)
  }
}
