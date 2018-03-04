import React, {Component} from 'react'
import config from "../config";
import Mail from "../mail";
import {Link} from "react-router-dom";
import Comments from "../comments";

class Pet extends Component {


  constructor() {
    super();

    this.state = {
      pet: null
    }
  }

  componentDidMount() {
    fetch(`${config.SERVER_URL}/pets/${this.props.match.params.slug}`)
      .then(r => r.json())
      .then(json => this.setState({pet: json}))
      .catch(e => console.warn(e))
  }

  render() {
    const {pet} = this.state;


    return pet ? <div className='row'>
      <div className='col-md-6'>
        <h1>{pet.name}</h1>
        <h4>Vendor: {pet.vendor}</h4>
        <p><Link to={`/pets/vendor/${pet.vendor}`} className="btn btn-primary">More Pets from {pet.vendor}</Link></p>

        <Mail pet={pet}/>
        <Comments topic={pet.slug} />

      </div>
      <div className='col-md-6'>
        <img style={{maxWidth: '70%'}} src={`${config.SERVER_URL}/images/${pet.image}`} alt={pet.name}/>
      </div>

    </div> : <span>Loading...</span>;
  }
}


export default Pet;