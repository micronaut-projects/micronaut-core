import React, {Component} from 'react';
import {BrowserRouter as Router, Route, Link} from "react-router-dom";
import Home from './home/index'
import Pets from './pets/index'
import Vendors from './vendors/index'
import Pet from "./pets/Pet";
import VendorPets from "./pets/VendorPets";
import logo from './images/logo.png'
import About from "./about";
import './App.css'


class App extends Component {
    render() {
        return (
            <Router>
                <div className="App">
                    <nav className="navbar navbar-expand-lg navbar-light bg-light">
                        <Link to="/" className="navbar-brand">
                          <img src={logo} className='micronaut-logo' alt='micronaut' /> Micronaut PetStore</Link>
                        <button className="navbar-toggler" type="button" data-toggle="collapse" data-target="#navbarNav"
                                aria-controls="navbarNav" aria-expanded="false" aria-label="Toggle navigation">
                            <span className="navbar-toggler-icon"></span>
                        </button>
                        <div className="collapse navbar-collapse" id="navbarNav">
                            <ul className='navbar-nav'>
                                <li className='nav-item'>
                                    <Link to="/" className="nav-link">Home</Link>
                                </li>
                                <li className='nav-item'>
                                    <Link to="/pets" className="nav-link">Pets</Link>
                                </li>
                                <li className='nav-item'>
                                    <Link to="/vendors" className="nav-link">Vendors</Link>
                                </li>
                                <li className='nav-item'>
                                    <Link to="/about" className="nav-link">About</Link>
                                </li>
                            </ul>
                        </div>
                    </nav>

                    <div className="container">

                        <Route exact path="/" component={Home} />
                        <Route exact path="/pets" component={Pets} />
                        <Route exact path="/pets/:slug" component={Pet} />
                        <Route exact path="/pets/vendor/:vendor" component={VendorPets} />
                        <Route exact path="/vendors" component={Vendors} />
                        <Route exact path="/about" component={About} />
                    </div>


                </div>
            </Router>
        );
    }
}

export default App;
