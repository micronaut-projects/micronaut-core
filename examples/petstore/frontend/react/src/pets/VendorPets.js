import React, {Component} from 'react'
import config from "../config";
import PetsLayout from "./PetsLayout";

class VendorPets extends Component {

  constructor() {
    super();

    this.state = {
      pets: []
    }
  }

  componentDidMount() {
    fetch(`${config.SERVER_URL}/pets/vendor/${this.props.match.params.vendor}`)
      .then(r => r.json())
      .then(json => this.setState({pets: json}))
      .catch(e => console.warn(e))
  }


  render() {
    const {pets} = this.state;
    const {match} = this.props;

    return <PetsLayout pets={pets} match={match} header={`Pets from ${match.params.vendor}`}/>
  }

}

export default VendorPets