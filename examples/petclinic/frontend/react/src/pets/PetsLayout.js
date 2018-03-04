import React, {Component} from 'react'
import {Route} from "react-router-dom";
import PetsGrid from "./PetsGrid";
import {array} from 'prop-types'

class PetsLayout extends Component {

  constructor() {
    super();

    this.state = {
      tab: 1
    }
  }

  petsForTab = (index) => {
    const {pets} = this.props;
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
    const {tab} = this.state
    const {match, header} = this.props

    return <div>
      <Route exact path={match.url}
             render={() => <div>
               <div className="jumbotron jumbotron-fluid">
                 <div className="container">
                   <h1 className="display-4">{header ? header : 'Pets'}</h1>
                 </div>
               </div>


               <ul className="nav nav-tabs">
                 <li className="nav-item">
                   <span className={`nav-link ${tab === 1 ? 'active' : ''}`}
                         onClick={() => this.switchTab(1)}>Cats</span>
                 </li>
                 <li className="nav-item">
                   <span className={`nav-link ${tab === 2 ? 'active' : ''}`}
                         onClick={() => this.switchTab( 2)}>Dogs</span>
                 </li>
               </ul>


               <PetsGrid pets={this.petsForTab(tab)}/>
             </div>}
      />
    </div>
  }
}

PetsLayout.propTypes = {
  pets: array
}


export default PetsLayout