package com.github.arhor.journey.ui

import org.godotengine.godot.GodotActivity

class MiniGameActivity : GodotActivity() {

    override fun getCommandLine(): MutableList<String> = super.getCommandLine().apply {
        add("--main-pack")
        add("res://minigame.pck")
    }
}
