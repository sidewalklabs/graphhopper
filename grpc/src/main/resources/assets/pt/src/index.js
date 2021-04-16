import App from "./view/App.js";
let root = document.createElement("div");
root.id = "root";
root.style.height = "100%";
document.body.appendChild(root);
ReactDOM.render(React.createElement(App), root);
