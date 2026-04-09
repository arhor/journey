package com.github.arhor.journey.ui

import org.godotengine.godot.GodotActivity

class MiniGameActivity : GodotActivity() {

    override fun getCommandLine(): MutableList<String> =
        super.getCommandLine()
            .toMutableList()
            .apply {
            add("--main-pack")
            add("res://minigame.pck")
            }
}
