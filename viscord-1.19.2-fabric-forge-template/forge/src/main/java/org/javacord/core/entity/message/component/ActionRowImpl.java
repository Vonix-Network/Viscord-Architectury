package org.javacord.core.entity.message.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.javacord.api.entity.message.component.ActionRow;
import org.javacord.api.entity.message.component.ComponentType;
import org.javacord.api.entity.message.component.LowLevelComponent;

import java.util.ArrayList;
import java.util.List;

public class ActionRowImpl extends ComponentImpl implements ActionRow {
    private final List<LowLevelComponent> components;

    public ActionRowImpl(JsonNode data) {
        super(ComponentType.ACTION_ROW);
        this.components = new ArrayList<>();
        if (data.has("components")) {
            for (JsonNode componentJson : data.get("components")) {
                int typeInt = componentJson.get("type").asInt();
                ComponentType type = ComponentType.fromId(typeInt);
                if (type == ComponentType.BUTTON) {
                    components.add(new ButtonImpl(componentJson));
                } else if (type.isSelectMenuType()) {
                    components.add(new SelectMenuImpl(componentJson));
                } else if (type == ComponentType.TEXT_INPUT) {
                    components.add(new TextInputImpl(componentJson));
                }
                // Unknown/unsupported component types (e.g. Discord Components v2: type 10, 17) are silently skipped
            }
        }
    }

    public ActionRowImpl(List<LowLevelComponent> data) {
        super(ComponentType.ACTION_ROW);
        this.components = data;
    }

    @Override
    public ObjectNode toJsonNode() {
        ObjectNode object = JsonNodeFactory.instance.objectNode();
        return toJsonNode(object);
    }

    public ObjectNode toJsonNode(ObjectNode object) throws IllegalStateException {
        object.put("type", ComponentType.ACTION_ROW.value());
        if (components.isEmpty()) {
            object.putArray("components");
            return object;
        }
        ArrayNode componentsJson = JsonNodeFactory.instance.objectNode().arrayNode();
        for (LowLevelComponent component : this.components) {
            if (component.getType() == ComponentType.BUTTON) {
                componentsJson.add(((ButtonImpl) component).toJsonNode());
            } else if (component.getType().isSelectMenuType()) {
                componentsJson.add(((SelectMenuImpl) component).toJsonNode());
            } else if (component.getType() == ComponentType.TEXT_INPUT) {
                componentsJson.add(((TextInputImpl) component).toJsonNode());
            } else if (component.getType() == ComponentType.ACTION_ROW) {
                throw new IllegalStateException("An action row can not contain an action row.");
            }
            // Unknown types skipped silently
        }
        object.set("components", componentsJson);
        return object;
    }

    @Override
    public List<LowLevelComponent> getComponents() {
        return components;
    }
}
