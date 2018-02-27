import React, {Component} from 'react';
import config from "../config";
import PetsLayout from "./PetsLayout";

class Pets extends Component {

  constructor() {
    super();

    this.state = {
      pets: []
    }
  }

  componentDidMount() {
    fetch(`${config.SERVER_URL}/pets`)
      .then(r => r.json())
      .then(json => this.setState({pets: json}))
      .catch(e => console.warn(e))
  }


  render() {
    const {pets} = this.state;
    const {match} = this.props;

    return <PetsLayout pets={pets} match={match}/>
  }
}

export default Pets;