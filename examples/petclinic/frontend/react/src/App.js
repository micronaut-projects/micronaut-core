import React, {Component} from 'react';
import {BrowserRouter as Router, Route, Link} from "react-router-dom";
import Home from './home/index'
import PetsLayout from './pets/index'
import Vendors from './vendors/index'


class App extends Component {
    render() {
        return (
            <Router>
                <div className="App">
                    <nav className="navbar navbar-expand-lg navbar-light bg-light">
                        <Link to="/" className="navbar-brand">Micronaut PetStore</Link>
                        <button className="navbar-toggler" type="button" data-toggle="collapse" data-target="#navbarNav"
                                aria-controls="navbarNav" aria-expanded="false" aria-label="Toggle navigation">
                            <span className="navbar-toggler-icon"></span>
                        </button>
                        <div className="collapse navbar-collapse" id="navbarNav">
                            <ul className='navbar-nav'>
                                <li className='nav-item'>
                                    <Link to="/" className="nav-link">HOME</Link>
                                </li>
                                <li className='nav-item'>
                                    <Link to="/pets" className="nav-link">PETS</Link>
                                </li>
                                <li className='nav-item'>
                                    <Link to="/vendors" className="nav-link">VENDORS</Link>
                                </li>
                            </ul>
                        </div>
                    </nav>

                    <div className="container">

                        <Route exact path="/" component={Home} />
                        <Route exact path="/pets" component={PetsLayout} />
                        <Route exact path="/vendors" component={Vendors} />
                    </div>


                </div>
            </Router>
        );
    }
}

export default App;
