function initializeCoreMod () {
  return {
    "eventbus": {
      "target": {
        "type": "CLASS",
        "name": "net.minecraftforge.eventbus.EventBus"
      },
      "transformer": function (classNode) {
      }
    }
  }
}