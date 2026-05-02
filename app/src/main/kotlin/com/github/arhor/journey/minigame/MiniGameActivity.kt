package com.github.arhor.journey.minigame

import org.godotengine.godot.GodotActivity

class MiniGameActivity : GodotActivity() {

    override fun getCommandLine() = super.getCommandLine().apply {
        add("--main-pack")
        add("res://minigame.pck")
    }
}
