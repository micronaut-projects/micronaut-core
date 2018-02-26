import React, {Component} from 'react'
import config from "../config";
import {Link} from "react-router-dom";

class RandomPet extends Component {

  constructor() {
    super()

    this.state = { pet: null }
  }

  componentDidMount() {

    fetch(`${config.SERVER_URL}/pets/random`)
      .then(r => r.json())
      .then(json => this.setState({pet: json}))
      .catch(e => console.warn(e))
  }

  render() {
    const {pet} = this.state;

    return pet ? <div className="card">
      <img className="card-img-top" src={`${config.SERVER_URL}/images/${pet.image}`} style={{ height: '18rem', objectFit: 'cover'}} alt={pet.name} />
      <div className="card-body">
        <h5 className="card-title">{pet.name}</h5>
        <Link to={`/pets/${pet.slug}`} className="btn btn-primary">More Info</Link>
      </div>
    </div> : null
  }

}

export default RandomPet