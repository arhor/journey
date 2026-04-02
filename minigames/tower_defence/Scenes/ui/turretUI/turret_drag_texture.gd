extends TextureRect

var turretType := ""

var can_grab = false
var grabbed_offset = Vector2()
var initial_pos := position
var placeholder = null
var pointer_position := Vector2()

func _ready():
	Globals.goldChanged.connect(check_can_purchase)

func _gui_input(event):
	if not check_can_purchase(Globals.currentMap.gold):
		return
	if is_pointer_press_event(event):
		pointer_position = get_event_position(event)
		can_grab = true
		grabbed_offset = position - pointer_position
		if not placeholder:
			visible = false
			create_placeholder()
	elif is_pointer_release_event(event):
		pointer_position = get_event_position(event)
		can_grab = false
		if placeholder:
			check_can_drop()

func _process(_delta):
	if can_grab:
		if placeholder:
			placeholder.position = pointer_position - get_viewport_rect().size / 2
		else:
			position = pointer_position + grabbed_offset

func _get_drag_data(_at_position):
	if placeholder:
		return
	if check_can_purchase(Globals.currentMap.gold):
		visible = false
		create_placeholder()

func check_can_drop():
	position = initial_pos
	can_grab = false
	visible = true
	if placeholder.can_place:
		build()
		placeholder = null
		return
	failed_drop()

func build():
	Globals.currentMap.gold -= Data.turrets[turretType]["cost"]
	placeholder.build()

func failed_drop():
	if placeholder:
		placeholder.queue_free()
		placeholder = null

func create_placeholder():
	var turretScene := load(Data.turrets[turretType]["scene"])
	var turret = turretScene.instantiate()
	turret.turret_type = turretType
	Globals.turretsNode.add_child(turret)
	placeholder = turret
	placeholder.set_placeholder()

func check_can_purchase(newGold):
	if turretType:
		if newGold >= Data.turrets[turretType]["cost"]:
			get_parent().can_purchase = true
			return true
		get_parent().can_purchase = false
		return false

func _input(event):
	if event is InputEventScreenDrag:
		pointer_position = event.position
	elif can_grab and (event is InputEventScreenTouch):
		pointer_position = get_event_position(event)

func is_pointer_press_event(event):
	return (event is InputEventScreenTouch and event.pressed)

func is_pointer_release_event(event):
	return (event is InputEventScreenTouch and not event.pressed)

func get_event_position(event):
	if event is InputEventScreenTouch or event is InputEventScreenDrag:
		return event.position
	return pointer_position
