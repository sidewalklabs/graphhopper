import { DateInput, TextInput, TimeInput, NumberInput } from "../components/Inputs.js";
import Point from "../../data/Point.js";
export default (({
                     search,
                     onSearchChange
                 }) => {
    return React.createElement(SearchInput, {
        search: search,
        onSearchChange: onSearchChange
    });
});
const SearchActionType = {
    FROM: "SearchActionType_FROM",
    TO: "SearchActionType_TO",
    DEPARTURE_TIME: "SearchActionType_DEPARTURE_TIME",
    DEPARTURE_DATE: "SearchActionType_DEPARTURE_DATE",
    TIME_OPTION: "SearchActionType_TIME_OPTION",
    PARETO_OPTION: "SearchActionType_PARETO_OPTION"
};

class SearchInput extends React.Component {
    constructor(props) {
        super(props);
        this.onChange = this.onChange.bind(this);
        this.handleInputChange = this.handleInputChange.bind(this);
        this.state = {
            isShowingOptions: false
        };
    }

    render() {
        return React.createElement("div", {
            className: "searchInput"
        }, React.createElement("div", {
            className: "locationInput"
        }, React.createElement(TextInput, {
            value: this.props.search.from != null ? this.props.search.from.toString() : "",
            label: "From",
            actionType: SearchActionType.FROM,
            onChange: this.onChange
        }), React.createElement(TextInput, {
            value: this.props.search.to != null ? this.props.search.to.toString() : "",
            label: "To",
            actionType: SearchActionType.TO,
            onChange: this.onChange
        })), React.createElement("div", {
            className: "timeSelect"
        }, React.createElement("div", {
            label: "Time",
            className: "dateTimeContainer"
        }, React.createElement(TimeInput, {
            value: this.props.search.departureDateTime.format("HH:mm"),
            onChange: this.onChange,
            actionType: SearchActionType.DEPARTURE_TIME
        }), React.createElement(DateInput, {
            value: this.props.search.departureDateTime.format("YYYY-MM-DD"),
            onChange: this.onChange,
            actionType: SearchActionType.DEPARTURE_DATE
        }))), React.createElement("div", null, React.createElement("button", {
            className: "optionsButton",
            onClick: e => this.setState(prevState => ({
                isShowingOptions: !prevState.isShowingOptions
            }))
        }, "Options"), this.state.isShowingOptions ? React.createElement(
            "div",
            null,
            React.createElement(NumberInput, {
                value: this.props.search.limitSolutions,
                label: "Limit # solutions",
                onChange: this.handleInputChange,
                actionType: "limitSolutions"
            }),
            React.createElement(NumberInput, {
                value: this.props.search.maxProfileDuration,
                label: "Max profile duration (minutes)",
                onChange: this.handleInputChange,
                actionType: "maxProfileDuration"
            }),
            React.createElement(NumberInput, {
                value: this.props.search.betaWalkTime,
                label: "Beta walk time (values > 1.0 disincentivize walking)",
                onChange: this.handleInputChange,
                actionType: "betaWalkTime"
            }),
            React.createElement(NumberInput, {
                value: this.props.search.limitStreetTimeSeconds,
                label: "Limit on walking time (seconds)",
                onChange: this.handleInputChange,
                actionType: "limitStreetTimeSeconds"
            }),
            React.createElement(NumberInput, {
                value: this.props.search.betaTransfers,
                label: "Beta transfers (values > 0 disincentivize transfers)",
                onChange: this.handleInputChange,
                actionType: "betaTransfers"
            }),
            /*
            React.createElement("div", {
                className: "checkbox"
            },React.createElement("input", {
                type: 'checkbox',
                value: this.props.search.usePareto,
                label: "Use pareto search (if unset, uses earliest-arrival-time algorithm)",
                onChange: this.handleInputChange,
                actionType: "usePareto"
            }))
            */
        ) : ""));
    }

    handleInputChange(action) {
        this.props.onSearchChange({[action.type]: action.value})
    }

    onChange(action) {
        console.log(action);

        switch (action.type) {
            case SearchActionType.FROM:
                this.props.onSearchChange({
                    from: Point.create(action.value)
                });
                break;

            case SearchActionType.TO:
                this.props.onSearchChange({
                    to: Point.create(action.value)
                });
                break;

            case SearchActionType.DEPARTURE_TIME:
                let departure1 = moment(action.value, "HH:mm");

                if (departure1.isValid()) {
                    departure1.year(this.props.search.departureDateTime.year());
                    departure1.month(this.props.search.departureDateTime.month());
                    departure1.date(this.props.search.departureDateTime.date());
                    this.props.onSearchChange({
                        departureDateTime: departure1
                    });
                }

                break;

            case SearchActionType.DEPARTURE_DATE:
                let departure2 = moment(action.value, "YYYY-MM-DD");

                if (departure2.isValid()) {
                    departure2.hour(this.props.search.departureDateTime.hour());
                    departure2.minute(this.props.search.departureDateTime.minute());
                    this.props.onSearchChange({
                        departureDateTime: departure2
                    });
                }

                break;

            case SearchActionType.TIME_OPTION:
                this.props.onSearchChange({
                    timeOption: parseInt(action.value)
                });
                break;

             /*
            case SearchActionType.PARETO_OPTION:
                if (action.value) {
                    this.props.onSearchChange({"usePareto": true});
                } else {

                }
                this.props.onSearchChange({"usePareto": action.value});
               */

            default:
                break;

        }
    }

}