import React, {Component} from 'react';
import PetsGrid from "./PetsGrid";
import config from "../config";
import Pet from "./Pet";
import {Route} from "react-router-dom";

class Pets extends Component {

  constructor() {
    super();

    this.state = {
      pets: [],
      tab: 1
    }
  }

  componentDidMount() {
    fetch(`${config.SERVER_URL}/pets`)
      .then(r => r.json())
      .then(json => this.setState({pets: json}))
      .catch(e => console.warn(e))
  }


  petsForTab = (index) => {
    const {pets} = this.state;
    let result
    switch (index) {
      case 1:
        result = pets.filter(p => p.type === 'CAT')
        break;
      case 2:
        result = pets.filter(p => p.type === 'DOG')
        break;
      default:
        result = pets
    }

    return result
  }

  switchTab(tab) {
    this.setState({tab})
  }

  render() {
    const {tab} = this.state;
    const {match} = this.props;


    return <div>
      <Route path="/pets/test" component={Pet} />
      <Route exact path={match.url}
        render={() => <div>
          <div className="jumbotron jumbotron-fluid">
            <div className="container">
              <h1 className="display-4">Pets</h1>
            </div>
          </div>


          <ul className="nav nav-tabs">
            <li className="nav-item">
              <span className={`nav-link ${tab === 1 ? 'active' : ''}`} onClick={() => this.switchTab(1)}>Cats</span>
            </li>
            <li className="nav-item">
              <span className={`nav-link ${tab === 2 ? 'active' : ''}`} onClick={() => this.switchTab(2)}>Dogs</span>
            </li>
          </ul>


          <PetsGrid pets={this.petsForTab(tab)} match={match}/>
        </div>}
      />
    </div>
  }
}

export default Pets;