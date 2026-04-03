
const Blockly = { Blocks: {}, Theme: { defineTheme: ()=>{} }, Themes: { Dark: {} }, Generator: function(){ this.forBlock={}; } };

// ================= ГЕНЕРАТОР КОДА =================
const SprauteGenerator = new Blockly.CodeGenerator('Spraute');

SprauteGenerator.scrub_ = function(block, code, opt_thisOnly) {
  const nextBlock = block.nextConnection && block.nextConnection.targetBlock();
  const nextCode = opt_thisOnly ? '' : SprauteGenerator.blockToCode(nextBlock);
  return code + nextCode;
};

// Категории блоков и цвета
const COLORS = {
  EVENTS: '#facc15',
  CONTROL: '#38bdf8',
  NPC: '#d1ff9f',
  PLAYER: '#ac8aff',
  ACTIONS: '#f472b6',
  FUNCTIONS: '#fb923c',
  VARIABLES: '#a855f7',
  UI: '#10b981',
  PARTICLES: '#f59e0b'
};

const ARG_LABELS = {
  'TARGET': 'цель', 'PLAYER': 'игрок', 'ENTITY': 'сущность', 'MESSAGE': 'сообщение',
  'TIME': 'сек', 'AMOUNT': 'кол-во', 'COUNT': 'кол-во', 'ITEM_ID': 'предмет',
  'WIDGET_ID': 'виджет', 'RADIUS': 'радиус', 'TASK_ID': 'задача', 'BLOCK_ID': 'блок',
  'COLOR': 'цвет', 'COMMAND': 'команда', 'SOUND_ID': 'звук', 'NAME': 'имя',
  'SLOT': 'слот', 'HAND': 'рука', 'VAL': 'значение', 'MAX_DIST': 'дист.',
  'TEMPLATE': 'шаблон', 'FIELD': 'поле', 'VALUE': 'знач.', 'DURATION': 'длит.',
  'TYPE': 'тип', 'SPEED': 'скорость', 'BONE_NAME': 'кость', 'BONE': 'кость',
  'DIST': 'дист.', 'X': 'X', 'Y': 'Y', 'Z': 'Z', 'CX': 'CX', 'CY': 'CY', 'CZ': 'CZ',
  'X1': 'X1', 'Y1': 'Y1', 'Z1': 'Z1', 'X2': 'X2', 'Y2': 'Y2', 'Z2': 'Z2',
  'HEIGHT': 'высота', 'DX': 'DX', 'DY': 'DY', 'DZ': 'DZ', 'NPC': 'NPC', 'ANCHOR': 'от'
};

function makeBlockWithShadows(act) {
  let inputs = {};
  if (act.args && act.args.length > 0) {
    act.args.forEach((arg, i) => {
      let shadowType = 'spraute_raw_value';
      let fieldName = 'VAL';
      let defaultVal = act.defaultArgs ? act.defaultArgs[i] : '0';
      
      if (['PLAYER', 'TARGET', 'ENTITY', 'ANCHOR'].includes(arg)) {
         shadowType = 'spraute_event_var';
         fieldName = 'VAR';
         defaultVal = defaultVal.replace(/"/g, ''); 
      } else if (arg === 'HAND') {
         shadowType = 'spraute_shadow_hand';
         fieldName = 'VAL';
      } else if (arg === 'COLOR') {
         shadowType = 'spraute_shadow_color';
         fieldName = 'VAL';
      }
      
      inputs[arg] = {
        "shadow": {
          "type": shadowType,
          "fields": {
            [fieldName]: defaultVal
          }
        }
      };
    });
  }
  
  return {
    "kind": "block",
    "type": act.type,
    "inputs": Object.keys(inputs).length > 0 ? inputs : undefined
  };
}

// --- ОПРЕДЕЛЕНИЕ БЛОКОВ ---

// ================= СОБЫТИЯ (EVENTS) =================
const ON_EVENTS = [
  { type: 'spraute_on_interact', label: 'При взаимодействии с', args: ['TARGET'], defaultArgs: ['target'], text: 'on interact' },
  { type: 'spraute_on_keybind', label: 'При нажатии клавиши', args: ['KEY'], defaultArgs: ['"key"'], text: 'on keybind' },
  { type: 'spraute_on_death', label: 'При смерти', args: ['TARGET'], defaultArgs: ['target'], text: 'on death' },
  { type: 'spraute_on_pickup', label: 'При поднятии предмета', args: ['NPC', 'ITEM_ID'], defaultArgs: ['npc', '"item_id"'], text: 'on pickup' },
  { type: 'spraute_on_uiClick', label: 'При клике в UI', args: ['PLAYER'], defaultArgs: ['player'], text: 'on uiClick' },
  { type: 'spraute_on_uiClose', label: 'При закрытии UI', args: ['PLAYER'], defaultArgs: ['player'], text: 'on uiClose' },
  { type: 'spraute_on_uiInput', label: 'При вводе в UI', args: ['PLAYER', 'WIDGET_ID'], defaultArgs: ['player', '"widget_id"'], text: 'on uiInput' },
  { type: 'spraute_on_position', label: 'В позиции', args: ['PLAYER', 'X', 'Y', 'Z', 'RADIUS'], defaultArgs: ['player', '0', '64', '0', '5'], text: 'on position' },
  { type: 'spraute_on_inventory', label: 'Предмет в инвентаре', args: ['PLAYER', 'ITEM_ID', 'COUNT'], defaultArgs: ['player', '"item_id"', '1'], text: 'on inventory' },
  { type: 'spraute_on_clickBlock', label: 'При клике по блоку', args: ['TARGET'], defaultArgs: ['"target"'], text: 'on clickBlock' },
  { type: 'spraute_on_breakBlock', label: 'При разрушении блока', args: ['TARGET'], defaultArgs: ['"target"'], text: 'on breakBlock' },
  { type: 'spraute_on_placeBlock', label: 'При установке блока', args: ['TARGET'], defaultArgs: ['"target"'], text: 'on placeBlock' },
  { type: 'spraute_on_chat', label: 'При сообщении в чат', args: ['PLAYER', 'MESSAGE'], defaultArgs: ['player', '"message"'], text: 'on chat' },
];

ON_EVENTS.forEach(ev => {
  Blockly.Blocks[ev.type] = {
    init: function() {
      this.appendDummyInput().appendField(ev.label);
      
      if (ev.args) {
        ev.args.forEach((arg, i) => {
          let label = ARG_LABELS[arg] || arg.toLowerCase();
          if (arg === 'NPC') {
            this.appendDummyInput().appendField(label).appendField(new Blockly.FieldDropdown(() => currentNpcs), arg);
          } else {
            this.appendValueInput(arg).appendField(label);
          }
        });
        this.setInputsInline(true);
      }
      
      this.appendStatementInput("DO").setCheck(null);
      this.setColour(COLORS.EVENTS);
    }
  };
});

// Генерация для ON
ON_EVENTS.forEach(ev => {
  SprauteGenerator.forBlock[ev.type] = function(block) {
    const args = ev.args.map(a => {
      if (a === 'NPC') return block.getFieldValue(a);
      return SprauteGenerator.valueToCode(block, a, 0) || 'null';
    }).join(', ');
    const doCode = SprauteGenerator.statementToCode(block, 'DO');
    const hId = "h_" + Math.random().toString(36).substr(2, 5);
    return `${ev.text}(${args}) -> ${hId} {\n${doCode}}\n`;
  };
});

// ================= УПРАВЛЕНИЕ (CONTROL) =================
Blockly.Blocks['spraute_if'] = {
  init: function() {
    this.appendValueInput("COND")
        .setCheck(null)
        .appendField("Если");
    this.appendStatementInput("DO")
        .setCheck(null)
        .appendField("То");
    this.setPreviousStatement(true, null);
    this.setNextStatement(true, null);
    this.setColour(COLORS.CONTROL);
  }
};
SprauteGenerator.forBlock['spraute_if'] = function(block) {
  let cond = SprauteGenerator.valueToCode(block, 'COND', 0) || 'true';
  const doCode = SprauteGenerator.statementToCode(block, 'DO');
  return `if (${cond}) {\n${doCode}}\n`;
};

// ================= ОЖИДАНИЕ (AWAIT) =================
const AWAIT_EVENTS = [
  { type: 'spraute_await_time', label: 'Ожидать время', args: ['TIME'], defaultArgs: ['1'], text: 'await time' },
  { type: 'spraute_await_interact', label: 'Ожидать взаимодействия', args: ['ENTITY'], defaultArgs: ['entity'], text: 'await interact' },
  { type: 'spraute_await_next', label: 'Ожидать следующий тик', args: [], defaultArgs: [], text: 'await next' },
  { type: 'spraute_await_keybind', label: 'Ожидать клавишу', args: ['KEY'], defaultArgs: ['"key"'], text: 'await keybind' },
  { type: 'spraute_await_death', label: 'Ожидать смерть', args: ['TARGET'], defaultArgs: ['target'], text: 'await death' },
  { type: 'spraute_await_pickup', label: 'Ожидать поднятия', args: ['NPC', 'AMOUNT', 'ITEM_ID'], defaultArgs: ['npc', '1', '"item_id"'], text: 'await pickup' },
  { type: 'spraute_await_task', label: 'Ожидать задачу', args: ['TASK_ID'], defaultArgs: ['"task_id"'], text: 'await task' },
  { type: 'spraute_await_uiClick', label: 'Ожидать клик UI', args: ['PLAYER'], defaultArgs: ['player'], text: 'await uiClick' },
  { type: 'spraute_await_uiClose', label: 'Ожидать закрытие UI', args: ['PLAYER'], defaultArgs: ['player'], text: 'await uiClose' },
  { type: 'spraute_await_uiInput', label: 'Ожидать ввод UI', args: ['PLAYER', 'WIDGET_ID'], defaultArgs: ['player', '"widget_id"'], text: 'await uiInput' },
  { type: 'spraute_await_position', label: 'Ожидать позицию', args: ['PLAYER', 'X', 'Y', 'Z', 'RADIUS'], defaultArgs: ['player', '0', '64', '0', '5'], text: 'await position' },
  { type: 'spraute_await_inventory', label: 'Ожидать инвентарь', args: ['PLAYER', 'ITEM_ID', 'COUNT'], defaultArgs: ['player', '"item_id"', '1'], text: 'await inventory' },
  { type: 'spraute_await_clickBlock', label: 'Ожидать клик по блоку', args: ['PLAYER', 'TARGET'], defaultArgs: ['player', '"target"'], text: 'await clickBlock' },
  { type: 'spraute_await_breakBlock', label: 'Ожидать разрушение блока', args: ['PLAYER', 'TARGET'], defaultArgs: ['player', '"target"'], text: 'await breakBlock' },
  { type: 'spraute_await_placeBlock', label: 'Ожидать установку блока', args: ['PLAYER', 'TARGET'], defaultArgs: ['player', '"target"'], text: 'await placeBlock' },
  { type: 'spraute_await_chat', label: 'Ожидать чат', args: ['PLAYER', 'MESSAGE'], defaultArgs: ['player', '"message"'], text: 'await chat' },
];

AWAIT_EVENTS.forEach(ev => {
  Blockly.Blocks[ev.type] = {
    init: function() {
      this.appendDummyInput().appendField(ev.label);
      ev.args.forEach(arg => {
        let label = ARG_LABELS[arg] || arg.toLowerCase();
        this.appendValueInput(arg).appendField(label);
      });
      this.setInputsInline(true);
      this.setPreviousStatement(true, null);
      this.setNextStatement(true, null);
      this.setColour(COLORS.CONTROL);
    }
  };
  SprauteGenerator.forBlock[ev.type] = function(block) {
    if (!ev.args || ev.args.length === 0) return `${ev.text}()\n`;
    const args = ev.args.map(a => SprauteGenerator.valueToCode(block, a, 0) || 'null').join(', ');
    return `${ev.text}(${args})\n`;
  };
});

// ================= ПЕРЕМЕННЫЕ (VARIABLES) =================
Blockly.Blocks['spraute_shadow_hand'] = {
  init: function() {
    this.appendDummyInput()
        .appendField(new Blockly.FieldDropdown([
          ["Основная рука", "\"main_hand\""],
          ["Вторая рука", "\"off_hand\""]
        ]), "VAL");
    this.setOutput(true, null);
    this.setColour(COLORS.VARIABLES);
  }
};
SprauteGenerator.forBlock['spraute_shadow_hand'] = function(block) {
  return [block.getFieldValue('VAL'), 0];
};

Blockly.Blocks['spraute_shadow_color'] = {
  init: function() {
    this.appendDummyInput()
        .appendField(new Blockly.FieldDropdown([
          ["Белый", "\"white\""],
          ["Красный", "\"red\""],
          ["Зеленый", "\"green\""],
          ["Синий", "\"blue\""],
          ["Желтый", "\"yellow\""],
          ["Золотой", "\"gold\""],
          ["Бирюзовый", "\"aqua\""],
          ["Фиолетовый", "\"light_purple\""],
        ]), "VAL");
    this.setOutput(true, null);
    this.setColour(COLORS.VARIABLES);
  }
};
SprauteGenerator.forBlock['spraute_shadow_color'] = function(block) {
  return [block.getFieldValue('VAL'), 0];
};

Blockly.Blocks['spraute_shadow_boolean'] = {
  init: function() {
    this.appendDummyInput()
        .appendField(new Blockly.FieldDropdown([
          ["Да (true)", "true"],
          ["Нет (false)", "false"]
        ]), "VAL");
    this.setOutput(true, null);
    this.setColour(COLORS.VARIABLES);
  }
};
SprauteGenerator.forBlock['spraute_shadow_boolean'] = function(block) {
  return [block.getFieldValue('VAL'), 0];
};

Blockly.Blocks['spraute_set_var'] = {
  init: function() {
    this.appendValueInput("VALUE")
        .setCheck(null)
        .appendField("Создать переменную")
        .appendField(new Blockly.FieldTextInput("x"), "VAR")
        .appendField("=");
    this.setPreviousStatement(true, null);
    this.setNextStatement(true, null);
    this.setColour(COLORS.VARIABLES);
  }
};
SprauteGenerator.forBlock['spraute_set_var'] = function(block) {
  const varName = block.getFieldValue('VAR');
  const value = SprauteGenerator.valueToCode(block, 'VALUE', 0) || '0';
  return `val ${varName} = ${value}\n`;
};

Blockly.Blocks['spraute_set_var_raw'] = {
  init: function() {
    this.appendDummyInput()
        .appendField("Переменная")
        .appendField(new Blockly.FieldTextInput("x"), "VAR")
        .appendField("=")
        .appendField(new Blockly.FieldTextInput("0"), "VALUE");
    this.setPreviousStatement(true, null);
    this.setNextStatement(true, null);
    this.setColour(COLORS.VARIABLES);
  }
};
SprauteGenerator.forBlock['spraute_set_var_raw'] = function(block) {
  const varName = block.getFieldValue('VAR');
  const value = block.getFieldValue('VALUE');
  return `val ${varName} = ${value}\n`;
};

Blockly.Blocks['spraute_get_var'] = {
  init: function() {
    this.appendDummyInput()
        .appendField(new Blockly.FieldTextInput("x"), "VAR");
    this.setOutput(true, null);
    this.setColour(COLORS.VARIABLES);
  }
};
SprauteGenerator.forBlock['spraute_get_var'] = function(block) {
  return [block.getFieldValue('VAR'), 0];
};

Blockly.Blocks['spraute_event_var'] = {
  init: function() {
    this.appendDummyInput()
        .appendField("Значение")
        .appendField(new Blockly.FieldDropdown(() => {
          let options = [
            ["Игрок (player)", "player"], 
            ["Цель (target)", "target"], 
            ["Событийный НИП (_event_npc)", "_event_npc"],
            ["Сущность (entity)", "entity"],
            ["Сообщение (message)", "message"],
            ["Блок (block)", "block"],
            ["Клавиша (key)", "key"],
            ["Количество (amount)", "amount"]
          ];
          if (typeof currentNpcs !== 'undefined' && currentNpcs.length > 0) {
            currentNpcs.forEach(npcArr => {
              if (npcArr[1] !== '_event_npc') {
                options.push(["НИП: " + npcArr[0], npcArr[1]]);
              }
            });
          }
          return options;
        }), "VAR");
    this.setOutput(true, null);
    this.setColour(COLORS.VARIABLES);
  }
};
SprauteGenerator.forBlock['spraute_event_var'] = function(block) {
  return [block.getFieldValue('VAR'), 0];
};

// ================= ACTIONS (Действия) =================
const PLAYER_ACTIONS = [
  { type: 'spraute_action_say', label: 'Отправить сообщение', args: ['TARGET', 'MESSAGE'], defaultArgs: ['player', '"Привет!"'], method: 'say' },
  { type: 'spraute_action_chat', label: 'В чат', args: ['MESSAGE'], defaultArgs: ['"Всем привет"'], method: 'chat' },
  { type: 'spraute_action_set_names_color', label: 'Цвет имен', args: ['COLOR'], defaultArgs: ['"white"'], method: 'setNamesColor' },
  { type: 'spraute_action_set_block', label: 'Установить блок', args: ['X', 'Y', 'Z', 'BLOCK_ID'], defaultArgs: ['0', '64', '0', '"stone"'], method: 'setBlock' },
  { type: 'spraute_action_give_item', label: 'Выдать предмет', args: ['PLAYER', 'ITEM_ID', 'COUNT'], defaultArgs: ['player', '"apple"', '1'], method: 'giveItem' },
  { type: 'spraute_action_execute', label: 'Выполнить команду', args: ['COMMAND'], defaultArgs: ['"say Hi"'], method: 'execute' },
  { type: 'spraute_action_task_done', label: 'Завершить задачу', args: ['TASK_ID'], defaultArgs: ['"task_id"'], method: 'taskDone' },
  { type: 'spraute_action_play_sound', label: 'Воспроизвести звук', args: ['PLAYER', 'SOUND_ID'], defaultArgs: ['player', '"sound_id"'], method: 'playSound' },
  { type: 'spraute_action_stop_sound', label: 'Остановить звук', args: ['PLAYER'], defaultArgs: ['player'], method: 'stopSound' },
  { type: 'spraute_action_npc_chat', label: 'NPC чат', args: ['PLAYER', 'NPC', 'MESSAGE', 'COLOR'], defaultArgs: ['player', 'npc', '"Привет"', '"white"'], method: 'npc_chat' },
  { type: 'spraute_action_teleport', label: 'Телепортировать', args: ['X', 'Y', 'Z'], defaultArgs: ['0', '64', '0'], method: 'teleport' },
  { type: 'spraute_action_damage', label: 'Урон', args: ['AMOUNT'], defaultArgs: ['1'], method: 'damage' }
];

const VALUE_ACTIONS = [
  { type: 'spraute_val_get_slot', label: 'Получить слот', args: ['PLAYER', 'SLOT'], defaultArgs: ['player', '0'], method: 'getSlot' },
  { type: 'spraute_val_has_item', label: 'Есть предмет?', args: ['PLAYER', 'ITEM_ID'], defaultArgs: ['player', '"apple"'], method: 'hasItem' },
  { type: 'spraute_val_count_item', label: 'Количество предметов', args: ['PLAYER', 'ITEM_ID'], defaultArgs: ['player', '"apple"'], method: 'countItem' },
  { type: 'spraute_val_get_player', label: 'Получить игрока по нику', args: ['NAME'], defaultArgs: ['"Steve"'], method: 'getPlayer' },
  { type: 'spraute_val_held_item', label: 'Предмет в руке', args: ['HAND'], defaultArgs: ['"main_hand"'], method: 'heldItem' },
  { type: 'spraute_val_held_item_nbt', label: 'NBT предмета', args: ['HAND'], defaultArgs: ['"main_hand"'], method: 'heldItemNbt' },
  { type: 'spraute_val_get_held_item', label: 'Предмет в руке игрока', args: ['PLAYER'], defaultArgs: ['player'], method: 'getHeldItem' },
  { type: 'spraute_val_int_str', label: 'Число в строку', args: ['VAL'], defaultArgs: ['1'], method: 'intStr' },
  { type: 'spraute_val_whole_str', label: 'Целое число в строку', args: ['VAL'], defaultArgs: ['1.5'], method: 'wholeStr' },
  { type: 'spraute_val_random', label: 'Случайное число', args: [], defaultArgs: [], method: 'random' },
  { type: 'spraute_val_list_create', label: 'Создать список', args: [], defaultArgs: [], method: 'listCreate' },
  { type: 'spraute_val_dict_create', label: 'Создать словарь', args: [], defaultArgs: [], method: 'dictCreate' },
  { type: 'spraute_val_raycast', label: 'Raycast', args: ['MAX_DIST'], defaultArgs: ['5'], method: 'raycast' }
];

const UI_ACTIONS = [
  { type: 'spraute_ui_open', label: 'Открыть UI', args: ['PLAYER', 'TEMPLATE'], defaultArgs: ['player', '"my_ui"'], method: 'uiOpen' },
  { type: 'spraute_ui_close', label: 'Закрыть UI', args: ['PLAYER'], defaultArgs: ['player'], method: 'uiClose' },
  { type: 'spraute_ui_overlay_open', label: 'Открыть Overlay', args: ['PLAYER', 'TEMPLATE'], defaultArgs: ['player', '"my_overlay"'], method: 'overlayOpen' },
  { type: 'spraute_ui_overlay_close', label: 'Закрыть Overlay', args: ['PLAYER'], defaultArgs: ['player'], method: 'overlayClose' },
  { type: 'spraute_ui_update', label: 'Обновить виджет', args: ['PLAYER', 'WIDGET_ID', 'FIELD', 'VALUE'], defaultArgs: ['player', '"label"', '"text"', '"New text"'], method: 'uiUpdate' },
  { type: 'spraute_ui_animate', label: 'Анимировать виджет', args: ['PLAYER', 'WIDGET_ID', 'FIELD', 'VALUE', 'DURATION'], defaultArgs: ['player', '"label"', '"pos"', '[10, 10]', '1.0'], method: 'uiAnimate' },
];

const PARTICLE_ACTIONS = [
  { type: 'spraute_particle_spawn', label: 'Заспавнить частицы', args: ['TYPE', 'X', 'Y', 'Z', 'COUNT', 'DX', 'DY', 'DZ', 'SPEED'], defaultArgs: ['"flame"', '0', '64', '0', '10', '0', '0', '0', '0.1'], method: 'particleSpawn' },
  { type: 'spraute_particle_line', label: 'Линия частиц', args: ['TYPE', 'X1', 'Y1', 'Z1', 'X2', 'Y2', 'Z2', 'COUNT', 'DX', 'DY', 'DZ', 'SPEED'], defaultArgs: ['"flame"', '0', '64', '0', '1', '64', '1', '10', '0', '0', '0', '0.1'], method: 'particleLine' },
  { type: 'spraute_particle_circle', label: 'Круг частиц', args: ['TYPE', 'CX', 'CY', 'CZ', 'RADIUS', 'COUNT', 'DX', 'DY', 'DZ', 'SPEED'], defaultArgs: ['"flame"', '0', '64', '0', '5', '10', '0', '0', '0', '0.1'], method: 'particleCircle' },
  { type: 'spraute_particle_spiral', label: 'Спираль частиц', args: ['TYPE', 'CX', 'CY', 'CZ', 'RADIUS', 'HEIGHT', 'COUNT', 'DX', 'DY', 'DZ', 'SPEED'], defaultArgs: ['"flame"', '0', '64', '0', '2', '5', '10', '0', '0', '0', '0.1'], method: 'particleSpiral' },
  { type: 'spraute_particle_start_bone', label: 'Частицы из кости', args: ['TASK_ID', 'NPC', 'BONE', 'TYPE', 'COUNT', 'DX', 'DY', 'DZ', 'SPEED'], defaultArgs: ['"task_id"', 'npc', '"head"', '"flame"', '10', '0', '0', '0', '0.1'], method: 'particleStartBone' },
  { type: 'spraute_particle_stop_bone', label: 'Остановить частицы', args: ['TASK_ID'], defaultArgs: ['"task_id"'], method: 'particleStopBone' },
];

PLAYER_ACTIONS.forEach(act => {
  Blockly.Blocks[act.type] = {
    init: function() {
      this.appendDummyInput().appendField(act.label);
      if (act.args) {
        act.args.forEach(arg => {
          let label = ARG_LABELS[arg] || arg.toLowerCase();
          this.appendValueInput(arg).appendField(label);
        });
        this.setInputsInline(true);
      }
      this.setPreviousStatement(true, null);
      this.setNextStatement(true, null);
      this.setColour(COLORS.ACTIONS);
    }
  };
  SprauteGenerator.forBlock[act.type] = function(block) {
    const args = act.args.map(a => SprauteGenerator.valueToCode(block, a, 0) || 'null').join(', ');
    return `${act.method}(${args})\n`;
  };
});

VALUE_ACTIONS.forEach(act => {
  Blockly.Blocks[act.type] = {
    init: function() {
      this.appendDummyInput().appendField(act.label);
      if (act.args) {
        act.args.forEach(arg => {
          let label = ARG_LABELS[arg] || arg.toLowerCase();
          this.appendValueInput(arg).appendField(label);
        });
        this.setInputsInline(true);
      }
      this.setOutput(true, null);
      this.setColour(COLORS.ACTIONS);
    }
  };
  SprauteGenerator.forBlock[act.type] = function(block) {
    if (!act.args || act.args.length === 0) return [`${act.method}()`, 0];
    const args = act.args.map(a => SprauteGenerator.valueToCode(block, a, 0) || 'null').join(', ');
    return [`${act.method}(${args})`, 0];
  };
});

UI_ACTIONS.forEach(act => {
  Blockly.Blocks[act.type] = {
    init: function() {
      this.appendDummyInput().appendField(act.label);
      if (act.args) {
        act.args.forEach(arg => {
          let label = ARG_LABELS[arg] || arg.toLowerCase();
          this.appendValueInput(arg).appendField(label);
        });
        this.setInputsInline(true);
      }
      this.setPreviousStatement(true, null);
      this.setNextStatement(true, null);
      this.setColour(COLORS.UI);
    }
  };
  SprauteGenerator.forBlock[act.type] = function(block) {
    const args = act.args.map(a => SprauteGenerator.valueToCode(block, a, 0) || 'null').join(', ');
    return `${act.method}(${args})\n`;
  };
});

PARTICLE_ACTIONS.forEach(act => {
  Blockly.Blocks[act.type] = {
    init: function() {
      this.appendDummyInput().appendField(act.label);
      if (act.args) {
        act.args.forEach(arg => {
          let label = ARG_LABELS[arg] || arg.toLowerCase();
          this.appendValueInput(arg).appendField(label);
        });
        this.setInputsInline(true);
      }
      this.setPreviousStatement(true, null);
      this.setNextStatement(true, null);
      this.setColour(COLORS.PARTICLES);
    }
  };
  SprauteGenerator.forBlock[act.type] = function(block) {
    const args = act.args.map(a => SprauteGenerator.valueToCode(block, a, 0) || 'null').join(', ');
    return `${act.method}(${args})\n`;
  };
});

Blockly.Blocks['spraute_action_get_nearest_player'] = {
  init: function() {
    this.appendDummyInput().appendField("Получить ближайшего игрока");
    this.appendValueInput("ANCHOR").appendField("от");
    this.setInputsInline(true);
    this.setOutput(true, null);
    this.setColour(COLORS.ACTIONS);
  }
};
SprauteGenerator.forBlock['spraute_action_get_nearest_player'] = function(block) {
  const anchor = SprauteGenerator.valueToCode(block, 'ANCHOR', 0) || 'target';
  return [`getNearestPlayer(${anchor})`, 0];
};

Blockly.Blocks['spraute_raw_code'] = {
  init: function() {
    this.appendDummyInput()
        .appendField("Код:")
        .appendField(new Blockly.FieldTextInput("say(player, \"Привет\")"), "CODE");
    this.setPreviousStatement(true, null);
    this.setNextStatement(true, null);
    this.setColour(COLORS.ACTIONS);
  }
};
SprauteGenerator.forBlock['spraute_raw_code'] = function(block) {
  const code = block.getFieldValue('CODE');
  return `${code}\n`;
};

Blockly.Blocks['spraute_raw_value'] = {
  init: function() {
    this.appendDummyInput()
        .appendField("Значение:")
        .appendField(new Blockly.FieldTextInput("\"Привет\""), "VAL");
    this.setOutput(true, null);
    this.setColour(COLORS.ACTIONS);
  }
};
SprauteGenerator.forBlock['spraute_raw_value'] = function(block) {
  return [block.getFieldValue('VAL'), 0];
};

// ================= ПОЛЬЗОВАТЕЛЬСКИЕ ФУНКЦИИ (FUNCTIONS) =================
Blockly.Blocks['spraute_def_fun'] = {
  init: function() {
    this.appendDummyInput()
        .appendField("Создать функцию")
        .appendField(new Blockly.FieldTextInput("myFunc"), "NAME")
        .appendField("(")
        .appendField(new Blockly.FieldTextInput("args"), "ARGS")
        .appendField(")");
    this.appendStatementInput("DO")
        .setCheck(null);
    this.setColour(COLORS.FUNCTIONS);
  }
};
SprauteGenerator.forBlock['spraute_def_fun'] = function(block) {
  const name = block.getFieldValue('NAME');
  const args = block.getFieldValue('ARGS');
  const doCode = SprauteGenerator.statementToCode(block, 'DO');
  return `fun ${name}(${args}) {\n${doCode}}\n`;
};

Blockly.Blocks['spraute_call_fun'] = {
  init: function() {
    this.appendDummyInput()
        .appendField("Вызвать")
        .appendField(new Blockly.FieldTextInput("myFunc"), "NAME")
        .appendField("(")
        .appendField(new Blockly.FieldTextInput("args"), "ARGS")
        .appendField(")");
    this.setPreviousStatement(true, null);
    this.setNextStatement(true, null);
    this.setColour(COLORS.FUNCTIONS);
  }
};
SprauteGenerator.forBlock['spraute_call_fun'] = function(block) {
  const name = block.getFieldValue('NAME');
  const args = block.getFieldValue('ARGS');
  return `${name}(${args})\n`;
};

// ================= NPC =================
let currentNpcs = [["_event_npc", "_event_npc"]];
let currentAnimations = [["(введите вручную)", ""]];

function updateDynamicLists(npcs, anims) {
  if (npcs && npcs.length > 0) {
    currentNpcs = npcs.map(n => [n, n]);
  }
  if (anims && anims.length > 0) {
    currentAnimations = anims.map(a => [a, a]);
  }
}

Blockly.Blocks['spraute_create_npc'] = {
  init: function() {
    this.appendDummyInput()
        .appendField("Создать НИП")
        .appendField("ID:")
        .appendField(new Blockly.FieldTextInput("myNpc"), "NAME");
    this.appendValueInput("DISPLAY_NAME").appendField("Имя:");
    this.appendValueInput("HP").appendField("Здоровье:");
    this.appendValueInput("X").appendField("X");
    this.appendValueInput("Y").appendField("Y");
    this.appendValueInput("Z").appendField("Z");
    this.setInputsInline(false);
    this.setPreviousStatement(true, null);
    this.setNextStatement(true, null);
    this.setColour(COLORS.NPC);
  }
};
SprauteGenerator.forBlock['spraute_create_npc'] = function(block) {
  const name = block.getFieldValue('NAME');
  const dName = SprauteGenerator.valueToCode(block, 'DISPLAY_NAME', 0) || '"НИП"';
  const hp = SprauteGenerator.valueToCode(block, 'HP', 0) || '20';
  const x = SprauteGenerator.valueToCode(block, 'X', 0) || '0';
  const y = SprauteGenerator.valueToCode(block, 'Y', 0) || '64';
  const z = SprauteGenerator.valueToCode(block, 'Z', 0) || '0';
  
  return `create npc ${name} {\n  name = ${dName}\n  hp = ${hp}\n  pos = ${x}, ${y}, ${z}\n}\n`;
};

const NPC_ACTIONS = [
  { type: 'spraute_npc_move_to', label: 'двигаться к', args: ['X', 'Y', 'Z'], defaultArgs: ['0', '64', '0'], method: 'moveTo' },
  { type: 'spraute_npc_always_move_to', label: 'всегда следовать за', args: ['TARGET'], defaultArgs: ['target'], method: 'alwaysMoveTo' },
  { type: 'spraute_npc_stop_move', label: 'остановить движение', args: [], defaultArgs: [], method: 'stopMove' },
  { type: 'spraute_npc_follow_until', label: 'следовать до', args: ['TARGET', 'DIST'], defaultArgs: ['target', '2'], method: 'followUntil' },
  { type: 'spraute_npc_look_at', label: 'смотреть на', args: ['TARGET'], defaultArgs: ['target'], method: 'lookAt' },
  { type: 'spraute_npc_always_look_at', label: 'всегда смотреть на', args: ['TARGET'], defaultArgs: ['target'], method: 'alwaysLookAt' },
  { type: 'spraute_npc_stop_look', label: 'перестать смотреть', args: [], defaultArgs: [], method: 'stopLook' },
  { type: 'spraute_npc_set_item', label: 'дать в руку', args: ['HAND', 'ITEM_ID'], defaultArgs: ['"main_hand"', '"sword"'], method: 'setItem' },
  { type: 'spraute_npc_remove_item', label: 'забрать из руки', args: ['HAND'], defaultArgs: ['"main_hand"'], method: 'removeItem' },
  { type: 'spraute_npc_pickup_only_from', label: 'подбирать только от', args: ['ENTITY'], defaultArgs: ['target'], method: 'pickupOnlyFrom' },
  { type: 'spraute_npc_pickup_any', label: 'подбирать всё', args: [], defaultArgs: [], method: 'pickupAny' },
  { type: 'spraute_npc_set_head_bone', label: 'кость головы', args: ['BONE_NAME'], defaultArgs: ['"head"'], method: 'setHeadBone' },
  { type: 'spraute_npc_remove', label: 'удалить', args: [], defaultArgs: [], method: 'remove' }
];

NPC_ACTIONS.forEach(act => {
  Blockly.Blocks[act.type] = {
    init: function() {
      let input = this.appendDummyInput()
          .appendField("NPC")
          .appendField(new Blockly.FieldDropdown(() => currentNpcs), "NPC")
          .appendField(act.label);
      
      act.args.forEach((arg, i) => {
        input.appendField(" ").appendField(new Blockly.FieldTextInput(act.defaultArgs[i]), arg);
      });
      
      this.setPreviousStatement(true, null);
      this.setNextStatement(true, null);
      this.setColour(COLORS.NPC);
    }
  };
  SprauteGenerator.forBlock[act.type] = function(block) {
    const npc = block.getFieldValue('NPC');
    const args = act.args.map(a => block.getFieldValue(a)).join(', ');
    return `${npc}.${act.method}(${args})\n`;
  };
});

Blockly.Blocks['spraute_npc_play_anim'] = {
  init: function() {
    this.appendDummyInput()
        .appendField("NPC")
        .appendField(new Blockly.FieldDropdown(() => currentNpcs), "NPC")
        .appendField("воспроизвести (")
        .appendField(new Blockly.FieldDropdown([["Один раз","playOnce"], ["Зациклено","playLoop"], ["Заморозить","playFreeze"]]), "TYPE")
        .appendField("):")
        .appendField(new Blockly.FieldDropdown(() => currentAnimations), "ANIM");
    this.setPreviousStatement(true, null);
    this.setNextStatement(true, null);
    this.setColour(COLORS.NPC);
  }
};
SprauteGenerator.forBlock['spraute_npc_play_anim'] = function(block) {
  const npc = block.getFieldValue('NPC');
  const type = block.getFieldValue('TYPE');
  const anim = block.getFieldValue('ANIM');
  return `${npc}.${type}("${anim}")\n`;
};


const SprauteTheme = Blockly.Theme.defineTheme('spraute_dark', {
  base: Blockly.Themes.Dark,
  blockStyles: {},
  categoryStyles: {},
  componentStyles: {
    workspaceBackgroundColour: 'transparent',
    toolboxBackgroundColour: 'var(--color-surface, #0b1a2f)',
    toolboxForegroundColour: 'var(--color-on-surface, #e2e8f0)',
    flyoutBackgroundColour: 'var(--color-bg, #040e1f)',
    flyoutForegroundColour: 'var(--color-on-surface, #e2e8f0)',
    flyoutOpacity: 0.95,
    scrollbarColour: 'var(--color-primary, #38bdf8)',
    insertionMarkerColour: '#fff',
    insertionMarkerOpacity: 0.3,
    scrollbarOpacity: 0.4,
    cursorColour: 'var(--color-primary, #d0d0d0)',
    blackBackground: 'var(--color-bg, #040e1f)'
  }
});

function applyBlocklyThemeColors(colors) {
  if (!Blockly.getMainWorkspace()) return;
  const theme = Blockly.Theme.defineTheme('spraute_dynamic', {
    base: Blockly.Themes.Dark,
    blockStyles: {},
    categoryStyles: {},
    componentStyles: {
      workspaceBackgroundColour: 'transparent',
      toolboxBackgroundColour: colors.surface || '#0b1a2f',
      toolboxForegroundColour: colors.text || '#ffffff',
      flyoutBackgroundColour: colors.bg || '#040e1f',
      flyoutForegroundColour: colors.text || '#ffffff',
      flyoutOpacity: 0.95,
      scrollbarColour: colors.primary || '#38bdf8',
      insertionMarkerColour: '#fff',
      insertionMarkerOpacity: 0.3,
      scrollbarOpacity: 0.4,
      cursorColour: colors.primary || '#d0d0d0',
      blackBackground: colors.bg || '#040e1f'
    }
  });
  Blockly.getMainWorkspace().setTheme(theme);
}

// ================= ПАРСЕР (Текст -> Блоки) =================
function attachValueInput(workspace, parentBlock, inputName, rawValue) {
  if (!rawValue) return;
  let val = rawValue.trim();
  let shadowType = 'spraute_raw_value';
  let fieldName = 'VAL';
  
  if (['player', 'target', 'entity', '_event_npc', 'message', 'block', 'key', 'npc', 'item_id', 'widget_id', 'amount', 'task_id'].includes(val)) {
    shadowType = 'spraute_event_var';
    fieldName = 'VAR';
  } else if (val === '"main_hand"' || val === '"off_hand"') {
    shadowType = 'spraute_shadow_hand';
    fieldName = 'VAL';
  } else if (val === 'true' || val === 'false') {
    shadowType = 'spraute_shadow_boolean';
    fieldName = 'VAL';
  }
  
  let valBlock = workspace.newBlock(shadowType);
  valBlock.setFieldValue(val.replace(/^"|"$/g, ''), fieldName);
  valBlock.initSvg();
  valBlock.render();
  
  let input = parentBlock.getInput(inputName);
  if (input && input.connection) {
    input.connection.connect(valBlock.outputConnection);
  }
}

function textToBlocks(text, workspace) {
  workspace.clear();
  let lines = text.split('\n');
  
  let currentParent = null; 
  let currentConnection = null;
  let blockStack = []; 
  
  let x = 20, y = 20;

  for (let i = 0; i < lines.length; i++) {
    let line = lines[i].trim();
    if (!line || line.startsWith('#')) continue;

    let newBlock = null;

    if (line === '}') {
      if (blockStack.length > 0) {
        let popped = blockStack.pop();
        if (!popped.isDummy) {
          currentParent = popped.parent;
          currentConnection = popped.nextConn;
        }
      }
      continue;
    }

    let lastComment = "";
    if (i > 0 && lines[i-1].trim().startsWith('# block_name:')) {
      lastComment = lines[i-1].trim().replace('# block_name:', '').trim();
    }

    // ON
    for (const ev of ON_EVENTS) {
      // Escape for regex
      const textRegex = ev.text.replace('(', '\\(').replace(')', '\\)');
      let regex = new RegExp(`^${textRegex}\\((.*?)\\)\\s*->\\s*\\w+\\s*\\{`);
      let match = line.match(regex);
      if (match) {
        newBlock = workspace.newBlock(ev.type);
        let argVals = match[1].split(',').map(s => s.trim());
        if (ev.args) {
          ev.args.forEach((arg, i) => {
            if (arg === 'NPC' && argVals[i]) {
              newBlock.setFieldValue(argVals[i], arg);
            } else {
              attachValueInput(workspace, newBlock, arg, argVals[i]);
            }
          });
        }
        break;
      }
    }

    // AWAIT
    if (!newBlock) {
      for (const ev of AWAIT_EVENTS) {
        let textRegex = ev.text.replace('(', '\\(').replace(')', '\\)');
        let regex = new RegExp(`^${textRegex}\\((.*?)\\)`);
        if (!ev.args || ev.args.length === 0) regex = new RegExp(`^${textRegex}`);
        
        let match = line.match(regex);
        if (match) {
          newBlock = workspace.newBlock(ev.type);
          if (ev.args && ev.args.length > 0 && match[1]) {
            let argVals = match[1].split(',').map(s => s.trim());
            ev.args.forEach((arg, i) => {
              attachValueInput(workspace, newBlock, arg, argVals[i]);
            });
          }
          break;
        }
      }
    }

    // val x = ...
    if (!newBlock) {
      let matchSetVar = line.match(/^val\s+(\w+)\s*=\s*(.+)/);
      if (matchSetVar) {
        newBlock = workspace.newBlock('spraute_set_var_raw');
        newBlock.setFieldValue(matchSetVar[1], 'VAR');
        newBlock.setFieldValue(matchSetVar[2], 'VALUE');
      }
    }

    // fun
    if (!newBlock) {
      let matchFun = line.match(/^fun\s+(\w+)\(([^)]*)\)\s*\{/);
      if (matchFun) {
        newBlock = workspace.newBlock('spraute_def_fun');
        newBlock.setFieldValue(matchFun[1], 'NAME');
        newBlock.setFieldValue(matchFun[2], 'ARGS');
        createCustomFunctionBlock(matchFun[1], lastComment);
      }
    }

    // if
    if (!newBlock) {
      let matchIf = line.match(/^if\s*\(([^)]+)\)\s*\{/);
      if (matchIf) {
        newBlock = workspace.newBlock('spraute_if');
        // В идеале мы должны парсить выражение внутри, но пока парсим как сырой текст,
        // однако у нас COND это value input. Чтобы установить текстовое значение в value input, 
        // нужно создать блок spraute_raw_value и присоединить.
        let valBlock = workspace.newBlock('spraute_raw_value');
        valBlock.setFieldValue(matchIf[1], 'VAL');
        valBlock.initSvg();
        valBlock.render();
        newBlock.getInput('COND').connection.connect(valBlock.outputConnection);
      }
    }

    // create npc
    if (!newBlock) {
      let matchCreateNpc = line.match(/^create\s+npc\s+(\w+)\s*\{/);
      if (matchCreateNpc) {
        newBlock = workspace.newBlock('spraute_create_npc');
        newBlock.setFieldValue(matchCreateNpc[1], 'NAME');
      }
    }

    // npc anim and actions
    if (!newBlock) {
      let matchNpcMethod = line.match(/^(\w+)\.(playOnce|playLoop|playFreeze|moveTo|alwaysMoveTo|stopMove|followUntil|lookAt|alwaysLookAt|stopLook|setItem|removeItem|pickupOnlyFrom|pickupAny|setHeadBone|remove)\(([^)]*)\)/);
      if (matchNpcMethod) {
        const method = matchNpcMethod[2];
        if (['playOnce', 'playLoop', 'playFreeze'].includes(method)) {
          newBlock = workspace.newBlock('spraute_npc_play_anim');
          newBlock.setFieldValue(matchNpcMethod[1], 'NPC');
          newBlock.setFieldValue(method, 'TYPE');
          newBlock.setFieldValue(matchNpcMethod[3].replace(/"/g, ''), 'ANIM');
        } else {
          const act = NPC_ACTIONS.find(a => a.method === method);
          if (act) {
            newBlock = workspace.newBlock(act.type);
            newBlock.setFieldValue(matchNpcMethod[1], 'NPC');
            const args = matchNpcMethod[3].split(',').map(s => s.trim());
            if (act.args) {
              act.args.forEach((argName, i) => {
                attachValueInput(workspace, newBlock, argName, args[i]);
              });
            }
          }
        }
      } else {
        let matchAnimFallback = line.match(/^(playOnce|playLoop|playFreeze)\("([^"]+)"\)/);
        if (matchAnimFallback) {
          newBlock = workspace.newBlock('spraute_npc_play_anim');
          newBlock.setFieldValue("_event_npc", 'NPC');
          newBlock.setFieldValue(matchAnimFallback[1], 'TYPE');
          newBlock.setFieldValue(matchAnimFallback[2], 'ANIM');
        }
      }
    }
    
    // general functions (PLAYER, UI, PARTICLES)
    if (!newBlock) {
      let matchFunc = line.match(/^(\w+)\(([^)]*)\)/);
      if (matchFunc) {
        let fnName = matchFunc[1];
        let rawArgs = matchFunc[2] ? matchFunc[2].split(',').map(s => s.trim()) : [];
        
        let foundAct = null;
        [...PLAYER_ACTIONS, ...UI_ACTIONS, ...PARTICLE_ACTIONS, ...VALUE_ACTIONS].forEach(act => {
          if (act.method === fnName) foundAct = act;
        });
        
        if (foundAct) {
          newBlock = workspace.newBlock(foundAct.type);
          if (foundAct.args) {
            foundAct.args.forEach((argName, i) => {
              attachValueInput(workspace, newBlock, argName, rawArgs[i]);
            });
          }
        } else if (fnName === 'getNearestPlayer') {
          newBlock = workspace.newBlock('spraute_action_get_nearest_player');
          attachValueInput(workspace, newBlock, 'ANCHOR', rawArgs[0]);
        }
      }
    }
    
    // custom fun call 
    if (!newBlock) {
      let matchCall = line.match(/^(\w+)\(([^)]*)\)/);
      if (matchCall && Blockly.Blocks['spraute_call_' + matchCall[1]]) {
        newBlock = workspace.newBlock('spraute_call_' + matchCall[1]);
        newBlock.setFieldValue(matchCall[2], 'ARGS');
      } else if (matchCall && !line.includes('{')) {
         newBlock = workspace.newBlock('spraute_call_fun');
         newBlock.setFieldValue(matchCall[1], 'NAME');
         newBlock.setFieldValue(matchCall[2], 'ARGS');
      }
    }

    // raw code
    if (!newBlock && !line.includes('}')) {
      newBlock = workspace.newBlock('spraute_raw_code');
      newBlock.setFieldValue(line.replace(/;$/, ''), 'CODE');
    }

    if (newBlock) {
      newBlock.initSvg();
      newBlock.render();

      if (currentConnection) {
        if (newBlock.previousConnection) {
          try {
            currentConnection.connect(newBlock.previousConnection);
            currentConnection = newBlock.nextConnection;
          } catch (e) {
            console.warn("Could not connect blocks:", e);
            newBlock.moveBy(x, y);
            y += 50;
            if (newBlock.nextConnection) currentConnection = newBlock.nextConnection;
          }
        } else if (newBlock.outputConnection) {
          // If it's a value block, just place it somewhere (normally it would attach to inputs)
          newBlock.moveBy(x, y);
          y += 50;
        } else {
           // Has no previous connection, just move it
           newBlock.moveBy(x, y);
           y += 50;
           if (newBlock.nextConnection) currentConnection = newBlock.nextConnection;
        }
      } else {
        newBlock.moveBy(x, y);
        y += 50;
        if (newBlock.nextConnection) currentConnection = newBlock.nextConnection;
      }

      if (line.endsWith('{')) {
        let stmtInput = newBlock.getInput('DO');
        if (stmtInput) {
          blockStack.push({
            parent: currentParent,
            nextConn: currentConnection,
            isDummy: false
          });
          currentParent = newBlock;
          currentConnection = stmtInput.connection;
        } else {
          blockStack.push({
            isDummy: true
          });
        }
      }
    }
  }
}

function createCustomFunctionBlock(funcName, displayName = "") {
  const blockName = 'spraute_call_' + funcName;
  const showName = displayName || ("Функция: " + funcName);
  
  if (!Blockly.Blocks[blockName]) {
    Blockly.Blocks[blockName] = {
      init: function() {
        this.appendDummyInput()
            .appendField(showName)
            .appendField("(")
            .appendField(new Blockly.FieldTextInput(""), "ARGS")
            .appendField(")");
        this.setPreviousStatement(true, null);
        this.setNextStatement(true, null);
        this.setColour(COLORS.FUNCTIONS);
      }
    };
    SprauteGenerator.forBlock[blockName] = function(block) {
      const args = block.getFieldValue('ARGS');
      return `${funcName}(${args})\n`;
    };
  }
}

const toolbox = {
  "kind": "categoryToolbox",
  "contents": [
    {
      "kind": "category",
      "name": "События (On)",
      "colour": COLORS.EVENTS,
      "contents": ON_EVENTS.map(ev => ({ "kind": "block", "type": ev.type }))
    },
    {
      "kind": "category",
      "name": "Ожидание (Await)",
      "colour": COLORS.CONTROL,
      "contents": AWAIT_EVENTS.map(ev => makeBlockWithShadows(ev))
    },
    {
      "kind": "category",
      "name": "Управление",
      "colour": COLORS.CONTROL,
      "contents": [
        { "kind": "block", "type": "spraute_if" }
      ]
    },
    {
      "kind": "category",
      "name": "Переменные",
      "colour": COLORS.VARIABLES,
      "contents": [
        { "kind": "block", "type": "spraute_set_var" },
        { "kind": "block", "type": "spraute_set_var_raw" },
        { "kind": "block", "type": "spraute_get_var" },
        { "kind": "block", "type": "spraute_event_var" },
        { "kind": "block", "type": "spraute_raw_value" }
      ]
    },
    {
      "kind": "category",
      "name": "Системные / Игрок",
      "colour": COLORS.ACTIONS,
      "contents": [
        makeBlockWithShadows({ type: "spraute_action_get_nearest_player", args: ["ANCHOR"], defaultArgs: ["target"] }),
        ...PLAYER_ACTIONS.map(act => makeBlockWithShadows(act)),
        ...VALUE_ACTIONS.map(act => makeBlockWithShadows(act)),
        { "kind": "block", "type": "spraute_raw_code" }
      ]
    },
    {
      "kind": "category",
      "name": "Интерфейс (UI)",
      "colour": COLORS.UI,
      "contents": UI_ACTIONS.map(act => makeBlockWithShadows(act))
    },
    {
      "kind": "category",
      "name": "Частицы",
      "colour": COLORS.PARTICLES,
      "contents": PARTICLE_ACTIONS.map(act => makeBlockWithShadows(act))
    },
    {
      "kind": "category",
      "name": "NPC",
      "colour": COLORS.NPC,
      "contents": [
        {
          "kind": "block",
          "type": "spraute_create_npc",
          "inputs": {
            "DISPLAY_NAME": { "shadow": { "type": "spraute_raw_value", "fields": { "VAL": "НИП" } } },
            "HP": { "shadow": { "type": "spraute_raw_value", "fields": { "VAL": "20" } } },
            "X": { "shadow": { "type": "spraute_raw_value", "fields": { "VAL": "0" } } },
            "Y": { "shadow": { "type": "spraute_raw_value", "fields": { "VAL": "64" } } },
            "Z": { "shadow": { "type": "spraute_raw_value", "fields": { "VAL": "0" } } }
          }
        },
        ...NPC_ACTIONS.map(act => makeBlockWithShadows(act)),
        { "kind": "block", "type": "spraute_npc_play_anim" }
      ]
    },
    {
      "kind": "category",
      "name": "Функции",
      "colour": COLORS.FUNCTIONS,
      "contents": [
        { "kind": "block", "type": "spraute_def_fun" },
        { "kind": "block", "type": "spraute_call_fun" }
      ]
    }
  ]
};

[...AWAIT_EVENTS, ...PLAYER_ACTIONS, ...VALUE_ACTIONS, ...UI_ACTIONS, ...PARTICLE_ACTIONS, ...NPC_ACTIONS].forEach(act => {
    try {
        makeBlockWithShadows(act);
    } catch(e) {
        console.error("FAILED for act:", act.type, e);
    }
});
makeBlockWithShadows({ type: "spraute_action_get_nearest_player", args: ["ANCHOR"], defaultArgs: ["target"] });
console.log("All makeBlockWithShadows done.");
