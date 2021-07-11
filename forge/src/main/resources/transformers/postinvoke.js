function initializeCoreMod () {
  return {
    "eventbus": {
      "target": {
        "type": "METHOD",
        "class": "net.minecraftforge.eventbus.EventBus",
        "methodName": "post",
        "methodDesc": "(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraftforge/eventbus/api/IEventBusInvokeDispatcher;)Z"
      },
      "transformer": function (methodNode) {
        var Opcodes = Java.type('org.objectweb.asm.Opcodes');
        var ASMAPI = Java.type('net.minecraftforge.coremod.api.ASMAPI');
        var InsnList = Java.type("org.objectweb.asm.tree.InsnList");
        var InsnNode = Java.type("org.objectweb.asm.tree.InsnNode");
        var LabelNode = Java.type("org.objectweb.asm.tree.LabelNode");
        var MethodNode = Java.type('org.objectweb.asm.tree.MethodNode');
        var MethodInsnNode = Java.type("org.objectweb.asm.tree.MethodInsnNode");
        var JumpInsnNode = Java.type("org.objectweb.asm.tree.JumpInsnNode");
        var LdcInsnNode = Java.type("org.objectweb.asm.tree.LdcInsnNode");
        var VarInsnNode = Java.type("org.objectweb.asm.tree.VarInsnNode");
        var MethodType = ASMAPI.MethodType;

        ASMAPI.log("INFO", "[Observable] Initializing EventBus transform");

        var instructions = methodNode.instructions;
        for (var i = 0; i < instructions.size; i++) {
          var insn = instructions.get(i);
          if (insn instanceof MethodInsnNode) {
            if (insn.opcode == Opcodes.INVOKESTATIC) {
              ASMAPI.log("INFO", "Found method " + insn.name + " " + insn.desc);
            }
          }
        }

        return methodNode;
      }
    }
  }
}