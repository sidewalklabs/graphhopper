import SecondaryText from "./SecondaryText.js";

const TextInput = props => {
    return Input(Object.assign({}, props, {
        type: "text"
    }));
};

const TimeInput = props => {
    return Input(Object.assign({}, props, {
        type: "time"
    }));
};

const DateInput = props => {
    return Input(Object.assign({}, props, {
        type: "date"
    }));
};

const NumberInput = props => {
    return Input(Object.assign({}, props, {
        type: "number"
    }));
};

const Input = ({
                   value,
                   label = "",
                   type,
                   actionType,
                   onChange
               }) => {
    return React.createElement("div", {
        className: "inputContainer"
    }, React.createElement(SecondaryText, null, label), React.createElement("input", {
        className: "input",
        type: type,
        value: value,
        onChange: e => onChange({
            type: actionType,
            value: e.target.value
        })
    }));
};

export { TextInput, TimeInput, DateInput, NumberInput };